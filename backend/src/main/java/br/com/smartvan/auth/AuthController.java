package br.com.smartvan.auth;

import br.com.smartvan.auth.dto.CadastroRequest;
import br.com.smartvan.auth.dto.LoginRequest;
import br.com.smartvan.auth.dto.LoginResponse;
import br.com.smartvan.auth.dto.UsuarioResponse;
import br.com.smartvan.comum.seguranca.UsuarioLogado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastro(@Valid @RequestBody CadastroRequest req) {
        return service.cadastrar(req);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @GetMapping("/me")
    public UsuarioResponse me() {
        return service.me(UsuarioLogado.atual().id());
    }
}
