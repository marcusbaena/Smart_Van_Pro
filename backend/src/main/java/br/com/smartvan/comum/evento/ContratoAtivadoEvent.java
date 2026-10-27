package br.com.smartvan.comum.evento;

/** Publicado pelo módulo de contratação quando um contrato vira ATIVO. Mensalidades escuta. */
public record ContratoAtivadoEvent(Long contratoId) {
}
