package br.com.smartvan.cadastro.controller;

import br.com.smartvan.cadastro.dto.EscolaResponse;
import br.com.smartvan.cadastro.service.EscolaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/escolas")
public class EscolaController {

    private final EscolaService service;

    public EscolaController(EscolaService service) {
        this.service = service;
    }

    @GetMapping
    public List<EscolaResponse> listar(@RequestParam(required = false) String cidade,
                                       @RequestParam(required = false) String nome) {
        return service.listar(cidade, nome);
    }
}
