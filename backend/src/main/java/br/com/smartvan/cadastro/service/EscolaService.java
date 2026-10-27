package br.com.smartvan.cadastro.service;

import br.com.smartvan.cadastro.dto.EscolaResponse;
import br.com.smartvan.cadastro.repository.EscolaBuscaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class EscolaService {

    private final EscolaBuscaRepository escolas;

    public EscolaService(EscolaBuscaRepository escolas) {
        this.escolas = escolas;
    }

    @Transactional(readOnly = true)
    public List<EscolaResponse> listar(String cidade, String nome) {
        return escolas.buscar(padrao(cidade), padrao(nome)).stream().map(EscolaResponse::de).toList();
    }

    private static String padrao(String texto) {
        return "%" + (texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT)) + "%";
    }
}
