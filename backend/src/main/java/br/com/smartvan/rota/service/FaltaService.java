package br.com.smartvan.rota.service;

import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.model.Aluno;
import br.com.smartvan.model.Falta;
import br.com.smartvan.repositorio.AlunoRepository;
import br.com.smartvan.rota.dto.FaltaRequest;
import br.com.smartvan.rota.dto.FaltaResponse;
import br.com.smartvan.rota.repository.RotaContratoRepository;
import br.com.smartvan.rota.repository.RotaFaltaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Faltas do aluno: o responsável avisa que o aluno não vai num dia, e a parada sai da rota. */
@Service
public class FaltaService {

    private final RotaFaltaRepository faltas;
    private final RotaContratoRepository vinculos;
    private final AlunoRepository alunos;
    private final Clock clock;

    public FaltaService(RotaFaltaRepository faltas, RotaContratoRepository vinculos, AlunoRepository alunos, Clock clock) {
        this.faltas = faltas;
        this.vinculos = vinculos;
        this.alunos = alunos;
        this.clock = clock;
    }

    @Transactional
    public FaltaResponse registrar(Long responsavelId, Long alunoId, FaltaRequest req) {
        Aluno aluno = alunoDoResponsavel(responsavelId, alunoId);
        if (req.data().isBefore(LocalDate.now(clock))) {
            throw RegraNegocioException.regra("DATA_PASSADA", "A data da falta não pode ser passada.");
        }
        if (faltas.existsByAlunoIdAndDataFalta(alunoId, req.data())) {
            throw RegraNegocioException.conflito("FALTA_JA_REGISTRADA", "Já existe falta registrada para este aluno nesta data.");
        }
        Falta f = new Falta();
        f.setAluno(aluno);
        f.setDataFalta(req.data());
        f.setMotivo(req.motivo() == null || req.motivo().isBlank() ? null : req.motivo().trim());
        return FaltaResponse.de(faltas.save(f));
    }

    /** Sem {@code mes}: faltas de hoje em diante. */
    @Transactional(readOnly = true)
    public List<FaltaResponse> listar(Long responsavelId, Long alunoId, YearMonth mes) {
        alunoDoResponsavel(responsavelId, alunoId);
        List<Falta> lista = mes == null
                ? faltas.findByAlunoIdAndDataFaltaGreaterThanEqualOrderByDataFalta(alunoId, LocalDate.now(clock))
                : faltas.findByAlunoIdAndDataFaltaBetweenOrderByDataFalta(alunoId, mes.atDay(1), mes.atEndOfMonth());
        return lista.stream().map(FaltaResponse::de).toList();
    }

    @Transactional
    public void remover(Long responsavelId, Long alunoId, LocalDate data) {
        alunoDoResponsavel(responsavelId, alunoId);
        Falta f = faltas.findByAlunoIdAndDataFalta(alunoId, data)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Falta"));
        faltas.delete(f);
    }

    private Aluno alunoDoResponsavel(Long responsavelId, Long alunoId) {
        Aluno aluno = alunos.findById(alunoId).orElseThrow(() -> RegraNegocioException.naoEncontrado("Aluno"));
        if (vinculos.vinculoAtivo(responsavelId, alunoId).isEmpty()) {
            throw RegraNegocioException.semPermissao();
        }
        return aluno;
    }
}
