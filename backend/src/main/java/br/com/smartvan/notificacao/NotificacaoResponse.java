package br.com.smartvan.notificacao;

import java.time.Instant;

public record NotificacaoResponse(Long id, TipoNotificacao tipo, String titulo, String mensagem,
                                  Long referenciaId, boolean lida, Instant dataCriacao) {

    static NotificacaoResponse de(Notificacao n) {
        return new NotificacaoResponse(n.getId(), n.getTipo(), n.getTitulo(), n.getMensagem(),
                n.getReferenciaId(), n.isLida(), n.getDataCriacao());
    }
}
