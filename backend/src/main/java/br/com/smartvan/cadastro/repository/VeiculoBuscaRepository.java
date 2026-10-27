package br.com.smartvan.cadastro.repository;

import br.com.smartvan.model.Veiculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VeiculoBuscaRepository extends JpaRepository<Veiculo, Long> {

    boolean existsByPlaca(String placa);

    List<Veiculo> findByMotoristaIdOrderByIdAsc(Long motoristaId);
}
