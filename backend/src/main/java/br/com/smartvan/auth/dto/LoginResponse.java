package br.com.smartvan.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiraEm, UsuarioResponse usuario) {
}
