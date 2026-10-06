package dev.poc.model;
import javax.persistence.*;
@Entity @Table(name="directors")
public class Director {
 @Id @Column(name="id") private Long id;
 @Column(name="name",nullable=false,length=120) private String name;
 public Director() {}
 public Long getId(){return id;} public String getName(){return name;}
}
