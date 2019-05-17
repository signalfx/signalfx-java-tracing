import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;

@Path("/test")
public class TestResource {
  @POST
  @Path("/hello/{name}")
  public String helloUser(@PathParam("name") final String name) {
    return "Hello " + name + "!";
  }

  @GET
  @Path("/blowup")
  public String blowup(@PathParam("name") final String name) throws RuntimeException {
    throw new WebApplicationException("Error");
  }
}
