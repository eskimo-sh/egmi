/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.egmi.configurations;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.egmi.security.JSONBackedUserDetailsManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.DelegatingAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private static final Logger logger = Logger.getLogger(WebSecurityConfiguration.class);

    public static final int MAXIMUM_SESSIONS_PER_USER = 10;

    public static final String LOGIN_PAGE_URL = "/login.html";
    public static final String INDEX_PAGE_URL = "/index.html";
    public static final String APP_PAGE_URL = "/app.html";

    @Autowired
    private JSONBackedUserDetailsManager userDetailsManager;

    //@Value("${security.userJsonFile}")
    //private String userJsonFilePath = "/tmp/egmi-users.json";

    @Value("${server.servlet.context-path:#{null}}")
    private String configuredContextPath = "";


    @Override
    protected void configure(HttpSecurity http) throws Exception {

        final String contextPath = getContextPath();

        http
            // authentication and authorization stuff
            .authorizeRequests()
                /*
                .antMatchers(LOGIN_PAGE_URL).permitAll()
                .antMatchers(INDEX_PAGE_URL).permitAll()
                .antMatchers("/css/**").permitAll()
                .antMatchers("/scripts/**").permitAll()
                .antMatchers("/js/**").permitAll()
                .antMatchers("/images/**").permitAll()
                .antMatchers("/img/**").permitAll()
                .antMatchers("/fonts/**").permitAll()
                .antMatchers("/html/**").permitAll()
                .antMatchers("/docs/**").permitAll()
                .antMatchers("/user/register").permitAll()
                .antMatchers("/user/resend").permitAll()
                .antMatchers("/user/reset").permitAll()
                .antMatchers("/user/activate").permitAll()
                .antMatchers("/user/do-reset").permitAll()
                .antMatchers("/user/change-password").permitAll()
                .antMatchers("/eula.html").permitAll()
                .antMatchers(APP_PAGE_URL).authenticated()
                .anyRequest().authenticated()
                */
                .antMatchers("/*").permitAll() // de-activating entirely authentication
                .and()
                .exceptionHandling()
                // way to avoid sending redirect to AJAX call (they tend not to like it)
                .authenticationEntryPoint((httpServletRequest, httpServletResponse, e) -> {
                    if (isAjax(httpServletRequest)) {
                        httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        httpServletResponse.sendRedirect(contextPath + INDEX_PAGE_URL);
                    }
                }).and()
            // own login stuff
            .formLogin()
                .loginPage(LOGIN_PAGE_URL).permitAll()
                .loginProcessingUrl("/login").permitAll()
                .defaultSuccessUrl("/app.html",true)
                .failureHandler(new DelegatingAuthenticationFailureHandler(
                        new LinkedHashMap<>(){{
                            put (AccountStatusException.class, (httpServletRequest, httpServletResponse, e) -> httpServletResponse.sendRedirect(contextPath + LOGIN_PAGE_URL + "?status"));
                            put (DisabledException.class, (httpServletRequest, httpServletResponse, e) -> httpServletResponse.sendRedirect(contextPath + LOGIN_PAGE_URL + "?status"));
                            put (BadCredentialsException.class, (httpServletRequest, httpServletResponse, e) -> httpServletResponse.sendRedirect(contextPath + LOGIN_PAGE_URL + "?error"));
                            put (UsernameNotFoundException.class, (httpServletRequest, httpServletResponse, e) -> httpServletResponse.sendRedirect(contextPath + LOGIN_PAGE_URL + "?error"));
                        }},
                        new SimpleUrlAuthenticationFailureHandler(contextPath + LOGIN_PAGE_URL + "?error")
                ))
                .and()
            .logout().permitAll()
                .and()
             // disabling CSRF security as long as not implemented backend side
            .csrf().disable()
            // disabling Same origin policy on iframes (eskimo may use this eventually)
            .headers().frameOptions().disable();
    }

    private String getContextPath() {
        if (StringUtils.isBlank(configuredContextPath)) {
            return "";
        } else {
            return (configuredContextPath.startsWith("/") ? "" : "/") + configuredContextPath;
        }
    }

    private boolean isAjax(HttpServletRequest request) {
        String acceptHeader = request.getHeader("accept");
        return acceptHeader != null && (
                 acceptHeader.contains("json") || acceptHeader.contains("javascript"));
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService())
                .passwordEncoder(new BCryptPasswordEncoder(11));
    }

    @Override
    public UserDetailsService userDetailsService() {
        return userDetailsManager;
    }

    public static class SessionTimeoutAuthSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
        public final Duration sessionTimeout;

        public SessionTimeoutAuthSuccessHandler(Duration sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws ServletException, IOException {
            req.getSession().setMaxInactiveInterval(Math.toIntExact(sessionTimeout.getSeconds()));
            super.onAuthenticationSuccess(req, res, auth);
        }
    }
}