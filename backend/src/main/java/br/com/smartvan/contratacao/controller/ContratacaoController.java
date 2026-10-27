package br.com.smartvan.contratacao.controller;

import br.com.smartvan.comum.seguranca.UsuarioLogado;
import br.com.smartvan.contratacao.dto.ContratoResponse;
import br.com.smartvan.contratacao.dto.MotoristaBusca;
import br.com.smartvan.contratacao.dto.PropostaRequest;
import br.com.smartvan.contratacao.service.ContratacaoService;
import br.com.smartvan.enums.StatusContrato;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ContratacaoController {

    private final ContratacaoService service;

    public ContratacaoController(ContratacaoService service) {
        this.service = service;
    }

    @GetMapping("/motoristas")
    @PreAuthorize("hasRole('RESPONSAVEL')")
    public List<MotoristaBusca> buscar(@RequestParam(required = false) Long escolaId,
                                       @RequestParam(required = false) String cidade,
                                       @RequestParam(required = false) String nome) {
        return service.buscar(UsuarioLogado.atual().id(), escolaId, cidade, nome);
    }

    @GetMapping("/motoristas/{id}")
    @PreAuthorize("hasRole('RESPONSAVEL')")
    public MotoristaBusca perfil(@PathVariable Long id) {
        return service.perfil(UsuarioLogado.atual().id(), id);
    }

    @PostMapping("/contratos")
    @PreAuthorize("hasRole('RESPONSAVEL')")
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoResponse propor(@Valid @RequestBody PropostaRequest req) {
        return service.propor(UsuarioLogado.atual().id(), req);
    }

    @GetMapping("/contratos")
    public List<ContratoResponse> listar(@RequestParam(required = false) StatusContrato status) {
        return service.listar(UsuarioLogado.atual(), status);
    }

    @GetMapping("/contratos/{id}")
    public ContratoResponse obter(@PathVariable Long id) {
        return service.obter(UsuarioLogado.atual(), id);
    }

    @PostMapping("/contratos/{id}/aceitar")
    @PreAuthorize("hasRole('MOTORISTA')")
    public ContratoResponse aceitar(@PathVariable Long id) {
        return service.aceitar(UsuarioLogado.atual().id(), id);
    }

    @PostMapping("/contratos/{id}/recusar")
    @PreAuthorize("hasRole('MOTORISTA')")
    public ContratoResponse recusar(@PathVariable Long id) {
        return service.recusar(UsuarioLogado.atual().id(), id);
    }
}
