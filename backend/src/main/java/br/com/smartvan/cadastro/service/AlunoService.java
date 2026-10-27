package br.com.smartvan.cadastro.service;

import br.com.smartvan.cadastro.dto.AlunoRequest;
import br.com.smartvan.cadastro.dto.AlunoResponse;
import br.com.smartvan.cadastro.repository.AlunoEnderecoBuscaRepository;
import br.com.smartvan.cadastro.repository.VinculoBuscaRepository;
import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.enums.StatusVinculo;
import br.com.smartvan.enums.TipoEndereco;
import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.AlunoEndereco;
import br.com.smartvan.model.Endereco;
import br.com.smartvan.model.Escola;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.model.ResponsavelAluno;
import br.com.smartvan.repositorio.AlunoRepository;
import br.com.smartvan.repositorio.EscolaRepository;
import br.com.smartvan.repositorio.ResponsavelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlunoService {

    private final AlunoRepository alunos;
    private final EscolaRepository escolas;
    private final ResponsavelRepository responsaveis;
    private final AlunoEnderecoBuscaRepository alunosEnderecos;
    private final VinculoBuscaRepository vinculos;
    private final EnderecoService enderecoService;

    public AlunoService(AlunoRepository alunos, EscolaRepository escolas, ResponsavelRepository responsaveis,
                        AlunoEnderecoBuscaRepository alunosEnderecos, VinculoBuscaRepository vinculos,
                        EnderecoService enderecoService) {
        this.alunos = alunos;
        this.escolas = escolas;
        this.responsaveis = responsaveis;
        this.alunosEnderecos = alunosEnderecos;
        this.vinculos = vinculos;
        this.enderecoService = enderecoService;
    }

    /** Grava alunos, alunos_enderecos (RESIDENCIAL, principal) e responsaveis_alunos (ATIVO, acompanha_rota). */
    @Transactional
    public AlunoResponse cadastrar(Long responsavelId, AlunoRequest req) {
        Responsavel responsavel = responsaveis.findById(responsavelId)
                .orElseThrow(RegraNegocioException::semPermissao);
        Escola escola = escolas.findById(req.escolaId())
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Escola"));

        Endereco endereco = enderecoService.criar(req.endereco());

        Aluno aluno = new Aluno();
        aluno.setNome(req.nome().trim());
        aluno.setDataNascimento(req.dataNascimento());
        aluno.setSerie(req.serie());
        aluno.setEscola(escola);
        aluno = alunos.save(aluno);

        AlunoEndereco ae = new AlunoEndereco();
        ae.setAluno(aluno);
        ae.setEndereco(endereco);
        ae.setTipo(TipoEndereco.RESIDENCIAL);
        ae.setPrincipal(true);
        alunosEnderecos.save(ae);

        ResponsavelAluno vinculo = new ResponsavelAluno();
        vinculo.setResponsavel(responsavel);
        vinculo.setAluno(aluno);
        vinculo.setParentesco(req.parentesco());
        vinculo.setStatusVinculo(StatusVinculo.ATIVO);
        vinculo.setAcompanhaRota(true);
        vinculos.save(vinculo);

        return AlunoResponse.de(aluno, req.parentesco(), endereco);
    }

    @Transactional(readOnly = true)
    public List<AlunoResponse> listar(Long responsavelId) {
        List<ResponsavelAluno> lista = vinculos.ativosDoResponsavel(responsavelId);
        if (lista.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lista.stream().map(v -> v.getAluno().getId()).toList();
        Map<Long, Endereco> principais = alunosEnderecos.principaisDosAlunos(ids).stream()
                .collect(Collectors.toMap(ae -> ae.getAluno().getId(), AlunoEndereco::getEndereco, (a, b) -> a));
        return lista.stream()
                .map(v -> AlunoResponse.de(v.getAluno(), v.getParentesco(), principais.get(v.getAluno().getId())))
                .toList();
    }
}
