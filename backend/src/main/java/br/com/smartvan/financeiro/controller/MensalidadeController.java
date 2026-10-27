package br.com.smartvan.financeiro.controller;

import br.com.smartvan.enums.StatusMensalidade;
import br.com.smartvan.financeiro.dto.BaixaRequest;
import br.com.smartvan.financeiro.dto.MensalidadeResponse;
import br.com.smartvan.financeiro.service.MensalidadeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MensalidadeController {

    private final MensalidadeService service;

    public MensalidadeController(MensalidadeService service) {
        this.service = service;
    }

    @GetMapping("/contratos/{id}/mensalidades")
    public List<MensalidadeResponse> porContrato(@PathVariable Long id) {
        return service.listarPorContrato(id);
    }

    @GetMapping("/responsavel/mensalidades")
    @PreAuthorize("hasRole('RESPONSAVEL')")
    public List<MensalidadeResponse> doResponsavel(@RequestParam(required = false) StatusMensalidade status) {
        return service.listarDoResponsavel(status);
    }

    @GetMapping("/motorista/cobrancas")
    @PreAuthorize("hasRole('MOTORISTA')")
    public List<MensalidadeResponse> cobrancas(@RequestParam(required = false) StatusMensalidade status,
                                               @RequestParam(required = false) String competencia) {
        return service.listarCobrancas(status, competencia);
    }

    @PostMapping("/mensalidades/{id}/baixa")
    @PreAuthorize("hasRole('MOTORISTA')")
    public MensalidadeResponse baixa(@PathVariable Long id, @RequestBody(required = false) BaixaRequest corpo) {
        return service.darBaixa(id, corpo == null ? null : corpo.dataPagamento());
    }
}
