package it.andreademartini.trading.paper.patternv1;
import jakarta.persistence.*;

@Entity
@Table(name="pattern_v1_paper_record")
public class PatternPaperEntity {
    @Id @Column(length=160) public String id;
    @Column(nullable=false,length=32) public String kind;
    @Lob @Column(nullable=false) public String payload;
    @Version public long revision;
    protected PatternPaperEntity() {}
    public PatternPaperEntity(String id,String kind,String payload){this.id=id;this.kind=kind;this.payload=payload;}
}
