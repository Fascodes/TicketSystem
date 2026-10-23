package dev.fascodes.ticketSystem.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    @Column(nullable = false, unique = true)
    private String name;

    protected Role(){}

    public Role(String name){
        this.name = name;
    }

    public Long getId() {
        return id;
    }


}
