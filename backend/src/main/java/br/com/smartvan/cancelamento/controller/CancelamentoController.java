package br.com.smartvan.cancelamento.controller;

import br.com.smartvan.cancelamento.dto.CancelamentoRequest;
import br.com.smartvan.contratacao.dto.ContratoResponse;
import br.com.smartvan.cancelamento.service.CancelamentoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class CancelamentoController {

    private final CancelamentoService service;

    public CancelamentoController(CancelamentoService service) {
        this.service = service;
    }

    @PostMapping("/contratos/{id}/cancelamento")
    public ContratoResponse solicitar(@PathVariable Long id, @Valid @RequestBody CancelamentoRequest corpo) {
        return service.solicitar(id, corpo.motivo());
    }
}
