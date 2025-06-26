package until.the.eternity.das.role.entity;


import jakarta.persistence.*;
import lombok.*;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.user.entity.UserRole;

import java.util.List;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @Enumerated(EnumType.STRING)
    private Name name;

    @Column(length = 255)
    private String description;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserRole> userRoles;
}