package br.com.smartvan.notificacao;

import br.com.smartvan.comum.seguranca.UsuarioLogado;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificacaoResponse> listar(@RequestParam(required = false) Boolean lidas) {
        return service.listar(UsuarioLogado.atual().id(), lidas);
    }

    @GetMapping("/nao-lidas/contagem")
    public Map<String, Long> contagem() {
        return Map.of("total", service.contarNaoLidas(UsuarioLogado.atual().id()));
    }

    @PostMapping("/{id}/lida")
    public ResponseEntity<Void> marcarComoLida(@PathVariable Long id) {
        service.marcarComoLida(UsuarioLogado.atual().id(), id);
        return ResponseEntity.noContent().build();
    }
}
