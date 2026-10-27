package br.com.smartvan.rota.controller;

import br.com.smartvan.comum.seguranca.UsuarioLogado;
import br.com.smartvan.rota.dto.FaltaRequest;
import br.com.smartvan.rota.dto.FaltaResponse;
import br.com.smartvan.rota.service.FaltaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/alunos/{alunoId}/faltas")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class FaltaController {

    private final FaltaService service;

    public FaltaController(FaltaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaltaResponse registrar(@PathVariable Long alunoId, @Valid @RequestBody FaltaRequest req) {
        return service.registrar(UsuarioLogado.atual().id(), alunoId, req);
    }

    @GetMapping
    public List<FaltaResponse> listar(@PathVariable Long alunoId,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return service.listar(UsuarioLogado.atual().id(), alunoId, mes);
    }

    @DeleteMapping("/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long alunoId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        service.remover(UsuarioLogado.atual().id(), alunoId, data);
    }
}
