package dev.poc.model;
import javax.persistence.*;
import com.sap.olingo.jpa.metadata.core.edm.annotation.EdmIgnore;
@Entity @Table(name="movies")
public class Movie {
 @Id @Column(name="id") private Long id;
 @Column(name="title",nullable=false,length=200) private String title;
 @Column(name="release_year",nullable=false) private Integer year;
 @Column(name="rating",nullable=false) private Double rating;
 @Column(name="genre",nullable=false,length=40) private String genre;
 @EdmIgnore @Column(name="director_id",insertable=false,updatable=false) private Long directorId;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="director_id",nullable=false) private Director director;
 public Movie() {}
 public Long getId(){return id;} public String getTitle(){return title;} public Integer getYear(){return year;}
 public Double getRating(){return rating;} public String getGenre(){return genre;} public Director getDirector(){return director;}
}
