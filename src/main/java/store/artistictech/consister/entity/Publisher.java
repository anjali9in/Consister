// Create new Entity called Publisher
// • POJO with properties for id, and publisherName, address, city, state, zip
// • Annotate with necessary JPA annotations
// • Add toString, equals, and hashCode
// • Create Spring Data JPA repository
// • Add to Bootstrap class and create new publisher
// • Verify database count using System.out.println


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Publisher {
    @Id
    private Long id;

    private String publisherName;

    private String address;
    private String city;
    private String state;
    private String zip;

}
