package br.com.smartvan.cadastro.controller;

import br.com.smartvan.cadastro.dto.EnderecoRequest;
import br.com.smartvan.cadastro.dto.EnderecoResponse;
import br.com.smartvan.cadastro.dto.EscolaResponse;
import br.com.smartvan.cadastro.dto.EscolaResumo;
import br.com.smartvan.cadastro.dto.EscolasAtendidasRequest;
import br.com.smartvan.cadastro.dto.PerfilMotoristaResponse;
import br.com.smartvan.cadastro.dto.VeiculoRequest;
import br.com.smartvan.cadastro.dto.VeiculoResponse;
import br.com.smartvan.cadastro.service.MotoristaCadastroService;
import br.com.smartvan.comum.seguranca.UsuarioLogado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/motorista")
@PreAuthorize("hasRole('MOTORISTA')")
public class MotoristaCadastroController {

    private final MotoristaCadastroService service;

    public MotoristaCadastroController(MotoristaCadastroService service) {
        this.service = service;
    }

    @PostMapping("/veiculos")
    @ResponseStatus(HttpStatus.CREATED)
    public VeiculoResponse cadastrarVeiculo(@Valid @RequestBody VeiculoRequest req) {
        return service.cadastrarVeiculo(UsuarioLogado.atual().id(), req);
    }

    @GetMapping("/veiculos")
    public List<VeiculoResponse> listarVeiculos() {
        return service.listarVeiculos(UsuarioLogado.atual().id());
    }

    @PutMapping("/escolas")
    public List<EscolaResumo> definirEscolas(@Valid @RequestBody EscolasAtendidasRequest req) {
        return service.definirEscolas(UsuarioLogado.atual().id(), req.escolaIds());
    }

    @GetMapping("/escolas")
    public List<EscolaResponse> listarEscolas() {
        return service.listarEscolas(UsuarioLogado.atual().id());
    }

    @PutMapping("/endereco-partida")
    public EnderecoResponse definirEnderecoPartida(@Valid @RequestBody EnderecoRequest req) {
        return service.definirEnderecoPartida(UsuarioLogado.atual().id(), req);
    }

    @GetMapping("/perfil")
    public PerfilMotoristaResponse perfil() {
        return service.perfil(UsuarioLogado.atual().id());
    }
}
