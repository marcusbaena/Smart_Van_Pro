package br.com.smartvan.cadastro.controller;

import br.com.smartvan.cadastro.dto.AlunoRequest;
import br.com.smartvan.cadastro.dto.AlunoResponse;
import br.com.smartvan.cadastro.service.AlunoService;
import br.com.smartvan.comum.seguranca.UsuarioLogado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/responsavel/alunos")
@PreAuthorize("hasRole('RESPONSAVEL')")
public class ResponsavelAlunoController {

    private final AlunoService service;

    public ResponsavelAlunoController(AlunoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlunoResponse cadastrar(@Valid @RequestBody AlunoRequest req) {
        return service.cadastrar(UsuarioLogado.atual().id(), req);
    }

    @GetMapping
    public List<AlunoResponse> listar() {
        return service.listar(UsuarioLogado.atual().id());
    }
}
