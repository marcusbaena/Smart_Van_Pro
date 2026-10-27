package br.com.smartvan.cadastro.service;

import br.com.smartvan.cadastro.dto.EnderecoRequest;
import br.com.smartvan.comum.maps.Coordenada;
import br.com.smartvan.comum.maps.MapsProvider;
import br.com.smartvan.model.Endereco;
import br.com.smartvan.repositorio.EnderecoRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Cria/atualiza endereços e geocodifica quando o cliente não mandou latitude/longitude. */
@Service
public class EnderecoService {

    private final EnderecoRepository enderecos;
    private final MapsProvider maps;

    public EnderecoService(EnderecoRepository enderecos, MapsProvider maps) {
        this.enderecos = enderecos;
        this.maps = maps;
    }

    public Endereco criar(EnderecoRequest req) {
        return atualizar(new Endereco(), req);
    }

    /** Copia os campos do request para o endereço (novo ou existente) e salva. */
    public Endereco atualizar(Endereco e, EnderecoRequest req) {
        e.setLogradouro(req.logradouro().trim());
        e.setNumero(req.numero().trim());
        e.setComplemento(req.complemento());
        e.setBairro(req.bairro().trim());
        e.setCidade(req.cidade().trim());
        e.setUf(req.uf());
        e.setCep(req.cep());
        if (req.latitude() != null && req.longitude() != null) {
            e.setLatitude(req.latitude());
            e.setLongitude(req.longitude());
        } else {
            Optional<Coordenada> c = maps.geocodificar(texto(req));
            e.setLatitude(c.map(Coordenada::latitude).orElse(null));
            e.setLongitude(c.map(Coordenada::longitude).orElse(null));
        }
        return enderecos.save(e);
    }

    private static String texto(EnderecoRequest r) {
        return "%s, %s, %s, %s - %s, %s".formatted(r.logradouro(), r.numero(), r.bairro(), r.cidade(), r.uf(), r.cep());
    }
}
