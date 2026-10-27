package br.com.smartvan.repositorio;

import br.com.smartvan.model.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

/** CRUD compartilhado (Wave 0). Não editar: consultas específicas vão num repository do próprio módulo. */
public interface EnderecoRepository extends JpaRepository<Endereco, Long> {
}
