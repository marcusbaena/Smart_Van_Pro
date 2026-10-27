package br.com.smartvan.rota.controller;

import br.com.smartvan.comum.seguranca.UsuarioLogado;
import br.com.smartvan.rota.dto.GerarRotaRequest;
import br.com.smartvan.rota.dto.RotaResponse;
import br.com.smartvan.rota.service.RotaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/motorista/rotas")
@PreAuthorize("hasRole('MOTORISTA')")
public class RotaController {

    private final RotaService service;

    public RotaController(RotaService service) {
        this.service = service;
    }

    @PostMapping("/gerar")
    public ResponseEntity<RotaResponse> gerar(@Valid @RequestBody GerarRotaRequest req) {
        RotaService.Resultado r = service.gerar(UsuarioLogado.atual().id(), req);
        return ResponseEntity.status(r.nova() ? HttpStatus.CREATED : HttpStatus.OK).body(r.rota());
    }

    @GetMapping
    public List<RotaResponse> listar(@RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.listar(UsuarioLogado.atual().id(), data);
    }

    @GetMapping("/{id}")
    public RotaResponse buscar(@PathVariable Long id) {
        return service.buscar(UsuarioLogado.atual().id(), id);
    }
}
