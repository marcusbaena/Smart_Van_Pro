package br.com.smartvan.auth;

import br.com.smartvan.auth.dto.CadastroRequest;
import br.com.smartvan.auth.dto.LoginRequest;
import br.com.smartvan.auth.dto.LoginResponse;
import br.com.smartvan.auth.dto.UsuarioResponse;
import br.com.smartvan.auth.repository.UsuarioAuthRepository;
import br.com.smartvan.comum.erro.RegraNegocioException;
import br.com.smartvan.enums.StatusConta;
import br.com.smartvan.enums.StatusMotorista;
import br.com.smartvan.enums.TipoUsuario;
import br.com.smartvan.model.Motorista;
import br.com.smartvan.model.Responsavel;
import br.com.smartvan.model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuthService {

    private final UsuarioAuthRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Clock clock;

    public AuthService(UsuarioAuthRepository usuarios, PasswordEncoder encoder, JwtService jwt, Clock clock) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional
    public UsuarioResponse cadastrar(CadastroRequest req) {
        String email = req.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw RegraNegocioException.conflito("EMAIL_JA_CADASTRADO", "E-mail já cadastrado.");
        }
        if (usuarios.existsByCpf(req.cpf())) {
            throw RegraNegocioException.conflito("CPF_JA_CADASTRADO", "CPF já cadastrado.");
        }
        Usuario u;
        if (req.tipo() == TipoUsuario.MOTORISTA) {
            Motorista m = new Motorista();
            m.setStatusValidacao(StatusMotorista.PENDENTE);
            u = m;
        } else {
            u = new Responsavel();
        }
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setSenha(encoder.encode(req.senha()));
        u.setCpf(req.cpf());
        u.setTelefone(req.telefone().trim());
        u.setTipo(req.tipo());
        u.setStatus(StatusConta.ATIVA);
        return UsuarioResponse.de(usuarios.save(u));
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        Usuario u = usuarios.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> encoder.matches(req.senha(), x.getSenha()))
                .orElseThrow(() -> new RegraNegocioException(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS",
                        "E-mail ou senha inválidos."));
        if (!u.estaAtiva()) {
            throw RegraNegocioException.semPermissao();
        }
        u.setUltimoAcesso(clock.instant());
        JwtService.TokenEmitido t = jwt.emitir(u.getId(), u.getTipo());
        return new LoginResponse(t.token(), t.expiraEm(), UsuarioResponse.de(u));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse me(Long usuarioId) {
        return usuarios.findById(usuarioId).map(UsuarioResponse::de)
                .orElseThrow(() -> new RegraNegocioException(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO",
                        "Faça login para continuar."));
    }
}
