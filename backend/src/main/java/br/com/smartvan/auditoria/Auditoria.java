package br.com.smartvan.auditoria;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "auditorias")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auditoria_id")
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "acao", length = 60, nullable = false)
    private String acao;

    @Column(name = "entidade", length = 60, nullable = false)
    private String entidade;

    @Column(name = "entidade_id", nullable = false)
    private Long entidadeId;

    @Column(name = "detalhes", columnDefinition = "text")
    private String detalhes;

    @Column(name = "data_registro", nullable = false, updatable = false)
    private Instant dataRegistro;
}
