package lt.satsyuk.distributed.audit.query.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {

    @Bean
    public Web3j web3j(Web3jProperties props) {
        return Web3j.build(new HttpService(props.getClientAddress().trim()));
    }
}

