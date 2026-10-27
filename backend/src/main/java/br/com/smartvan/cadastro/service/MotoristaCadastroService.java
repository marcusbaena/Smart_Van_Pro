package br.com.smartvan.cadastro.service;

import br.com.smartvan.auth.dto.UsuarioResponse;
import br.com.smartvan.cadastro.dto.EnderecoRequest;
import br.com.smartvan.cadastro.dto.EnderecoResponse;
import br.com.smartvan.cadastro.dto.EscolaResponse;
import br.com.smartvan.cadastro.dto.EscolaResumo;
import br.com.smartvan.cadastro.dto.PerfilMotoristaResponse;
import br.com.smartvan.cadastro.dto.VeiculoRequest;
import br.com.smartvan.cadastro.dto.VeiculoResponse;
import br.com.smartvan.cadastro.repository.VeiculoBuscaRepository;
import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.model.Endereco;
import br.com.smartvan.model.Escola;
import br.com.smartvan.model.Motorista;
import br.com.smartvan.model.Veiculo;
import br.com.smartvan.repositorio.EscolaRepository;
import br.com.smartvan.repositorio.MotoristaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Veículos, escolas atendidas, ponto de partida e perfil do motorista. */
@Service
public class MotoristaCadastroService {

    private final MotoristaRepository motoristas;
    private final VeiculoBuscaRepository veiculos;
    private final EscolaRepository escolas;
    private final EnderecoService enderecoService;

    public MotoristaCadastroService(MotoristaRepository motoristas, VeiculoBuscaRepository veiculos,
                                    EscolaRepository escolas, EnderecoService enderecoService) {
        this.motoristas = motoristas;
        this.veiculos = veiculos;
        this.escolas = escolas;
        this.enderecoService = enderecoService;
    }

    private Motorista motorista(Long id) {
        return motoristas.findById(id).orElseThrow(RegraNegocioException::semPermissao);
    }

    @Transactional
    public VeiculoResponse cadastrarVeiculo(Long motoristaId, VeiculoRequest req) {
        Motorista m = motorista(motoristaId);
        String placa = req.placa().toUpperCase(Locale.ROOT);
        if (veiculos.existsByPlaca(placa)) {
            throw RegraNegocioException.conflito("PLACA_JA_CADASTRADA", "Placa já cadastrada.");
        }
        Veiculo v = new Veiculo(m, req.tipo(), placa, req.modelo().trim(), req.capacidade(), req.ano());
        return VeiculoResponse.de(veiculos.save(v));
    }

    @Transactional(readOnly = true)
    public List<VeiculoResponse> listarVeiculos(Long motoristaId) {
        return veiculos.findByMotoristaIdOrderByIdAsc(motoristaId).stream().map(VeiculoResponse::de).toList();
    }

    @Transactional
    public List<EscolaResumo> definirEscolas(Long motoristaId, List<Long> escolaIds) {
        Motorista m = motorista(motoristaId);
        Set<Long> ids = new HashSet<>(escolaIds);
        List<Escola> encontradas = escolas.findAllById(ids);
        if (encontradas.size() != ids.size()) {
            throw RegraNegocioException.naoEncontrado("Escola");
        }
        m.getEscolasAtendidas().clear();
        m.getEscolasAtendidas().addAll(encontradas);
        return ordenadas(m).stream().map(EscolaResumo::de).toList();
    }

    @Transactional(readOnly = true)
    public List<EscolaResponse> listarEscolas(Long motoristaId) {
        return ordenadas(motorista(motoristaId)).stream().map(EscolaResponse::de).toList();
    }

    @Transactional
    public EnderecoResponse definirEnderecoPartida(Long motoristaId, EnderecoRequest req) {
        Motorista m = motorista(motoristaId);
        Endereco atual = m.getEnderecoPartida() != null ? m.getEnderecoPartida() : new Endereco();
        Endereco salvo = enderecoService.atualizar(atual, req);
        m.setEnderecoPartida(salvo);
        return EnderecoResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public PerfilMotoristaResponse perfil(Long motoristaId) {
        Motorista m = motorista(motoristaId);
        return new PerfilMotoristaResponse(
                UsuarioResponse.de(m),
                m.getEnderecoPartida() == null ? null : EnderecoResponse.de(m.getEnderecoPartida()),
                listarVeiculos(motoristaId),
                ordenadas(m).stream().map(EscolaResumo::de).toList());
    }

    private static List<Escola> ordenadas(Motorista m) {
        return m.getEscolasAtendidas().stream().sorted(Comparator.comparing(Escola::getNome)).toList();
    }
}
