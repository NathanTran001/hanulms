package fit.se2.hanulms.Repository;

import fit.se2.hanulms.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends LMSUserRepository<Admin, Long> {
}
