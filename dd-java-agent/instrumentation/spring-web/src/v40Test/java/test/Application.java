//Modified by SignalFx
package test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;

@SpringBootApplication
public class Application {
  public static final String USER = "username";
  public static final String PASS = "password";
  private static final String ROLE = "USER";

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Component("restTemplate")
  public class RestTemplateTestClient extends RestTemplate {
    private class NoopErrorHandler extends DefaultResponseErrorHandler {
      @Override
      public void handleError(ClientHttpResponse response) throws IOException {}
    }

    public RestTemplateTestClient() {
      super();
      this.setErrorHandler(new NoopErrorHandler());
    }

    private void removeBasicAuth() {
      ArrayList<ClientHttpRequestInterceptor> updated = new ArrayList<>();
      ArrayList<ClientHttpRequestInterceptor> interceptors = (ArrayList<ClientHttpRequestInterceptor>) this.getInterceptors();
      for (final ClientHttpRequestInterceptor interceptor : interceptors) {
        if (!(interceptor instanceof BasicAuthorizationInterceptor)) {
          updated.add(interceptor);
        }
      }
      this.setInterceptors(updated);
    }

    RestTemplate withBasicAuth(String username, String password) {
      removeBasicAuth();
      ArrayList<ClientHttpRequestInterceptor> interceptors = (ArrayList<ClientHttpRequestInterceptor>) this.getInterceptors();
      interceptors.add(new BasicAuthorizationInterceptor(username, password));
      this.setInterceptors(interceptors);
      return this;
    }

    RestTemplate withoutBasicAuth() {
      removeBasicAuth();
      return this;
    }
  }

  @Configuration
  @EnableWebSecurity
  public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(final HttpSecurity http) throws Exception {
      http.csrf().disable().authorizeRequests().anyRequest().authenticated().and().httpBasic();
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
      return new InMemoryUserDetailsManager(
          User.withUsername(USER).password(PASS).roles(ROLE).build());
    }
  }
}
