package br.com.smartvan.rota.service;

import br.com.smartvan.cadastro.repository.AlunoEnderecoBuscaRepository;
import br.com.smartvan.cadastro.repository.VeiculoBuscaRepository;
import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.comum.maps.Coordenada;
import br.com.smartvan.comum.maps.MapsProvider;
import br.com.smartvan.comum.maps.RotaOtimizada;
import br.com.smartvan.enums.StatusRota;
import br.com.smartvan.enums.TipoParada;
import br.com.smartvan.enums.TipoRota;
import br.com.smartvan.model.*;
import br.com.smartvan.notificacao.NotificacaoService;
import br.com.smartvan.notificacao.TipoNotificacao;
import br.com.smartvan.repositorio.EnderecoRepository;
import br.com.smartvan.repositorio.EscolaRepository;
import br.com.smartvan.repositorio.MotoristaRepository;
import br.com.smartvan.rota.dto.GerarRotaRequest;
import br.com.smartvan.rota.dto.RotaResponse;
import br.com.smartvan.rota.maps.OrdenadorPorProximidade;
import br.com.smartvan.rota.repository.RotaBuscaRepository;
import br.com.smartvan.rota.repository.RotaContratoRepository;
import br.com.smartvan.rota.repository.RotaFaltaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** UC15: gera a rota do dia (IDA ou VOLTA) com os alunos de contratos ATIVO na escola, menos as faltas. */
@Service
public class RotaService {

    private static final Logger log = LoggerFactory.getLogger(RotaService.class);
    private static final LocalTime SAIDA_IDA = LocalTime.of(6, 30);
    private static final LocalTime SAIDA_VOLTA = LocalTime.of(12, 30);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    /** @param nova true se foi criada agora (201), false se reaproveitada (200) */
    public record Resultado(RotaResponse rota, boolean nova) {
    }

    private final MotoristaRepository motoristas;
    private final EscolaRepository escolas;
    private final EnderecoRepository enderecos;
    private final VeiculoBuscaRepository veiculos;
    private final AlunoEnderecoBuscaRepository enderecosAlunos;
    private final RotaContratoRepository contratos;
    private final RotaFaltaRepository faltas;
    private final RotaBuscaRepository rotas;
    private final MapsProvider maps;
    private final NotificacaoService notificacoes;
    private final Clock clock;

    public RotaService(MotoristaRepository motoristas, EscolaRepository escolas, EnderecoRepository enderecos,
                       VeiculoBuscaRepository veiculos, AlunoEnderecoBuscaRepository enderecosAlunos,
                       RotaContratoRepository contratos, RotaFaltaRepository faltas, RotaBuscaRepository rotas,
                       MapsProvider maps, NotificacaoService notificacoes, Clock clock) {
        this.motoristas = motoristas;
        this.escolas = escolas;
        this.enderecos = enderecos;
        this.veiculos = veiculos;
        this.enderecosAlunos = enderecosAlunos;
        this.contratos = contratos;
        this.faltas = faltas;
        this.rotas = rotas;
        this.maps = maps;
        this.notificacoes = notificacoes;
        this.clock = clock;
    }

    @Transactional
    public Resultado gerar(Long motoristaId, GerarRotaRequest req) {
        if (req.data().isBefore(LocalDate.now(clock))) {
            throw RegraNegocioException.regra("DATA_PASSADA", "A data da rota não pode ser passada.");
        }
        Motorista motorista = motoristas.findById(motoristaId).orElseThrow(() -> RegraNegocioException.naoEncontrado("Motorista"));
        Escola escola = escolas.findById(req.escolaId()).orElseThrow(() -> RegraNegocioException.naoEncontrado("Escola"));

        List<Aluno> alunos = alunosDaRota(motoristaId, escola.getId(), req.data());
        if (alunos.isEmpty()) {
            throw RegraNegocioException.regra("ROTA_SEM_ALUNOS", "Nenhum aluno para esta rota (contratos ativos, menos faltas).");
        }
        Endereco partida = motorista.getEnderecoPartida();
        if (partida == null) {
            throw RegraNegocioException.regra("ENDERECO_PARTIDA_OBRIGATORIO", "Cadastre o endereço de partida antes de gerar a rota.");
        }
        Veiculo veiculo = escolherVeiculo(motoristaId, req.veiculoId());

        // reaproveita a rota planejada só se o conjunto de alunos for o mesmo
        Set<Long> idsAlunos = alunos.stream().map(Aluno::getId).collect(Collectors.toSet());
        List<Rota> existentes = rotas.findByMotoristaIdAndDataRotaAndTipoAndEscolaIdAndStatus(
                motoristaId, req.data(), req.tipo(), escola.getId(), StatusRota.PLANEJADA);
        for (Rota existente : existentes) {
            Set<Long> idsExistente = existente.getParadas().stream().map(ParadaRota::getAluno)
                    .filter(Objects::nonNull).map(Aluno::getId).collect(Collectors.toSet());
            if (idsExistente.equals(idsAlunos)) {
                return new Resultado(RotaResponse.de(existente), false);
            }
        }
        rotas.deleteAll(existentes);
        rotas.flush();

        Map<Long, Endereco> casas = enderecosAlunos.principaisDosAlunos(idsAlunos).stream()
                .collect(Collectors.toMap(ae -> ae.getAluno().getId(), AlunoEndereco::getEndereco, (a, b) -> a));
        List<Coordenada> coordsAlunos = new ArrayList<>();
        for (Aluno a : alunos) {
            Endereco casa = casas.get(a.getId());
            if (casa == null) {
                throw RegraNegocioException.regra("ENDERECO_SEM_COORDENADAS", "O aluno " + a.getNome() + " não tem endereço com coordenadas.");
            }
            coordsAlunos.add(coordenada(casa));
        }
        Endereco enderecoEscola = escola.getEndereco();
        boolean ida = req.tipo() == TipoRota.IDA;
        Coordenada coordPartida = coordenada(ida ? partida : enderecoEscola);
        Coordenada coordDestino = coordenada(ida ? enderecoEscola : partida);

        LocalTime saida = req.horarioSaida() != null ? req.horarioSaida() : (ida ? SAIDA_IDA : SAIDA_VOLTA);
        Instant saidaInstant = LocalDateTime.of(req.data(), saida).atZone(clock.getZone()).toInstant();

        boolean otimizada = true;
        RotaOtimizada resultado = null;
        try {
            resultado = maps.otimizarRota(coordPartida, coordsAlunos, coordDestino, saidaInstant);
            if (!valido(resultado, alunos.size())) {
                resultado = null;
            }
        } catch (Exception e) {
            log.warn("Otimização de rota indisponível, usando proximidade: {}", e.getClass().getSimpleName());
        }
        if (resultado == null) {
            otimizada = false;
            resultado = OrdenadorPorProximidade.ordenar(coordPartida, coordsAlunos, coordDestino);
        }

        Rota rota = new Rota();
        rota.setMotorista(motorista);
        rota.setVeiculo(veiculo);
        rota.setEscola(escola);
        rota.setDataRota(req.data());
        rota.setTipo(req.tipo());
        rota.setStatus(StatusRota.PLANEJADA);
        rota.setOtimizada(otimizada);
        rota.setPolyline(otimizada ? resultado.polyline() : null);
        rota.setHorarioInicio(saida.truncatedTo(ChronoUnit.MINUTES));
        rota.setHorarioFim(saida.plusSeconds(resultado.segundosTotal()).truncatedTo(ChronoUnit.MINUTES));
        rota.setTempoEstimadoMinutos((int) Math.round(resultado.segundosTotal() / 60.0));
        rota.setDistanciaKm(BigDecimal.valueOf(resultado.metrosTotal()).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP));

        int ordem = 1;
        if (!ida) {
            rota.adicionarParada(parada(ordem++, TipoParada.EMBARQUE, null, enderecoEscola, saida));
        }
        Map<Long, LocalTime> previstoPorAluno = new HashMap<>();
        TipoParada tipoAluno = ida ? TipoParada.EMBARQUE : TipoParada.DESEMBARQUE;
        for (int i = 0; i < resultado.ordemParadas().size(); i++) {
            Aluno aluno = alunos.get(resultado.ordemParadas().get(i));
            LocalTime previsto = saida.plusSeconds(resultado.segundosAteCadaParada().get(i));
            previstoPorAluno.put(aluno.getId(), previsto);
            rota.adicionarParada(parada(ordem++, tipoAluno, aluno, casas.get(aluno.getId()), previsto));
        }
        if (ida) {
            rota.adicionarParada(parada(ordem, TipoParada.DESEMBARQUE, null, enderecoEscola,
                    saida.plusSeconds(resultado.segundosTotal())));
        }
        rota = rotas.save(rota);

        notificarResponsaveis(rota, alunos, previstoPorAluno, escola, ida);
        return new Resultado(RotaResponse.de(rota), true);
    }

    @Transactional(readOnly = true)
    public List<RotaResponse> listar(Long motoristaId, LocalDate data) {
        LocalDate dia = data != null ? data : LocalDate.now(clock);
        return rotas.findByMotoristaIdAndDataRotaOrderByHorarioInicioAscIdAsc(motoristaId, dia).stream()
                .map(RotaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public RotaResponse buscar(Long motoristaId, Long rotaId) {
        Rota rota = rotas.findById(rotaId).orElseThrow(() -> RegraNegocioException.naoEncontrado("Rota"));
        if (!rota.getMotorista().getId().equals(motoristaId)) {
            throw RegraNegocioException.semPermissao();
        }
        return RotaResponse.de(rota);
    }

    // ------------------------------------------------------------------ apoio

    private List<Aluno> alunosDaRota(Long motoristaId, Long escolaId, LocalDate data) {
        List<Aluno> todos = contratos.alunosAtivosNaEscola(motoristaId, escolaId).stream().distinct().toList();
        if (todos.isEmpty()) {
            return todos;
        }
        Set<Long> comFalta = new HashSet<>(faltas.alunosComFalta(data, todos.stream().map(Aluno::getId).toList()));
        return todos.stream().filter(a -> !comFalta.contains(a.getId())).toList();
    }

    private Veiculo escolherVeiculo(Long motoristaId, Long veiculoId) {
        if (veiculoId != null) {
            Veiculo v = veiculos.findById(veiculoId).orElseThrow(() -> RegraNegocioException.naoEncontrado("Veículo"));
            if (!v.getMotorista().getId().equals(motoristaId)) {
                throw RegraNegocioException.semPermissao();
            }
            return v;
        }
        List<Veiculo> doMotorista = veiculos.findByMotoristaIdOrderByIdAsc(motoristaId);
        if (doMotorista.isEmpty()) {
            throw RegraNegocioException.regra("VEICULO_OBRIGATORIO", "Cadastre um veículo para gerar a rota.");
        }
        if (doMotorista.size() > 1) {
            throw RegraNegocioException.regra("VEICULO_AMBIGUO", "Você tem mais de um veículo: informe o veiculoId.");
        }
        return doMotorista.getFirst();
    }

    /** Coordenada do endereço; tenta geocodificar (e gravar) se faltar. */
    private Coordenada coordenada(Endereco e) {
        if (e.getLatitude() == null || e.getLongitude() == null) {
            Optional<Coordenada> c = Optional.empty();
            try {
                c = maps.geocodificar("%s, %s, %s, %s - %s, %s".formatted(
                        e.getLogradouro(), e.getNumero(), e.getBairro(), e.getCidade(), e.getUf(), e.getCep()));
            } catch (Exception ex) {
                log.warn("Geocodificação indisponível: {}", ex.getClass().getSimpleName());
            }
            if (c.isEmpty()) {
                throw new RegraNegocioException(HttpStatus.UNPROCESSABLE_CONTENT, "ENDERECO_SEM_COORDENADAS",
                        "Não foi possível obter as coordenadas do endereço " + e.getLogradouro() + ", " + e.getNumero() + ".");
            }
            e.setLatitude(c.get().latitude());
            e.setLongitude(c.get().longitude());
            enderecos.save(e);
        }
        return new Coordenada(e.getLatitude(), e.getLongitude());
    }

    private static boolean valido(RotaOtimizada r, int n) {
        if (r == null || r.ordemParadas() == null || r.segundosAteCadaParada() == null) {
            return false;
        }
        if (r.ordemParadas().size() != n || r.segundosAteCadaParada().size() != n) {
            return false;
        }
        Set<Integer> vistos = new HashSet<>(r.ordemParadas());
        return vistos.size() == n && vistos.stream().allMatch(i -> i != null && i >= 0 && i < n);
    }

    private static ParadaRota parada(int ordem, TipoParada tipo, Aluno aluno, Endereco endereco, LocalTime previsto) {
        ParadaRota p = new ParadaRota();
        p.setOrdem(ordem);
        p.setTipoParada(tipo);
        p.setAluno(aluno);
        p.setEndereco(endereco);
        p.setHorarioPrevisto(previsto.truncatedTo(ChronoUnit.MINUTES));
        return p;
    }

    private void notificarResponsaveis(Rota rota, List<Aluno> alunos, Map<Long, LocalTime> previstos, Escola escola, boolean ida) {
        Map<Long, Aluno> porId = alunos.stream().collect(Collectors.toMap(Aluno::getId, a -> a));
        String data = rota.getDataRota().format(DIA_MES);
        for (ResponsavelAluno v : contratos.vinculosAtivos(porId.keySet())) {
            Aluno aluno = porId.get(v.getId().getAlunoId());
            String hora = previstos.get(aluno.getId()).format(HORA);
            String msg = ida
                    ? "Rota de ida de %s para %s: %s será buscado(a) por volta das %s.".formatted(data, escola.getNome(), aluno.getNome(), hora)
                    : "Rota de volta de %s de %s: %s chega em casa por volta das %s.".formatted(data, escola.getNome(), aluno.getNome(), hora);
            notificacoes.notificar(v.getId().getResponsavelId(), TipoNotificacao.ROTA_GERADA,
                    "Rota do dia gerada", msg, rota.getId());
        }
    }
}
