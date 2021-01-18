/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 *  Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


package ch.niceideas.common.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpClientTest {

    @Test
    public void testRequestValidationPattern() throws Exception {
        
        assertEquals(
                null,
                getMatch("http://finance.yahoo.com"));
        
        assertEquals(
                null,
                getMatch("http://finance.yahoo.com/"));

        assertEquals(
                null,
                getMatch("https://abs.twimg.com/responsive-web/client-web/vendors~main.95208665.js"));

        assertEquals(
                "?",
                getMatch("https://8643885.fls.doubleclick.net/activityi;src=8643885;type=0global;cat=0_glo0;ord=6158835380190;gtm=2wg9g1;auiddc=1888159734.1601154192;u1=www.24heures.ch;u2=%2F;~oref=https%3A%2F%2Fwww.24heures.ch%2F?"));
        
        assertEquals(
                "?lang=en&date1=05/01/12&date=05/10/12&date_fmt=us&exch=EUR&expr2=CHF&margin_fixed=0&SUBMIT=Get+Table&format=CSV&redirected=1",
                getMatch("http://www.oanda.com/convert/fxhistory?lang=en&date1=05/01/12&date=05/10/12&date_fmt=us&exch=EUR&expr2=CHF&margin_fixed=0&SUBMIT=Get+Table&format=CSV&redirected=1"));
        
        assertEquals(
                null,
                getMatch("https://plus.google.com/_/scs/apps-static/_/ss/koz.home.-pl6beeyj3vcy.L.I7.O/amAGAICGCTiEsAAAAACIU76KYAFYEBAMAO/rsAItRSTMJ_m3XwTkLAaprP2LcqVl9hfPPWg"));
        
        assertEquals(
                null,
                getMatch("https://plus.google.com/_/scs/apps-static/_/ss/k=oz.home.ahpme8b3foix.L.I7.O/am=AGAIEcAiEZcAAAAAEAo76KYANYEBAMAO/rs=AItRSTMFrY9wDbH9XmmgR6bByzp6RWO22w"));
        
        assertEquals(
                null,
                getMatch("https://plus.google.com/_/scs/apps-static/_/js/k=oz.home.de.Lqy7UbCaT1Q.O/m=b,prc/am=AGAIEcAiEZcAAAAAEAo76KYANYEBAMAO/rt=h/d=1/rs=AItRSTOW8XKDde-o7cSDfQa6IxcdRrLawA"));
        
        assertEquals(
                null,
                getMatch("http://slot1.images2.wikia.nocookie.net/__load/-/cb%3D62486%26debug%3Dfalse%26lang%3Den%26only%3Dscripts%26skin%3Doasis/amd|wikia.tracker.stub|wikia.cookies,geo,window"));

        assertEquals(
                "?site=143573;size=1x1;e=0;dt=0;category=tt1kyd71;kw=smj6rrvp%202md%20l5nvl7xv6mt%20v5o9;rnd=(1600079281471)",
                getMatch("https://pbid.pro-market.net/engine?site=143573;size=1x1;e=0;dt=0;category=tt1kyd71;kw=smj6rrvp%202md%20l5nvl7xv6mt%20v5o9;rnd=(1600079281471)"));

        assertEquals(
                "?mt_exid=10012&redir=https://tag.crsspxl.com/m.gif?mmid=[MM_UUID]",
                getMatch("https://sync.mathtag.com/sync/img?mt_exid=10012&redir=https://tag.crsspxl.com/m.gif?mmid=[MM_UUID]"));

        assertEquals(
                "?https://tag.crsspxl.com/m.gif?anid=$UID",
                getMatch("https://ib.adnxs.com/getuid?https://tag.crsspxl.com/m.gif?anid=$UID"));

        assertEquals(
                "?tnid=$!{TURN_UUID}",
                getMatch("https://d.turn.com/r/dd/id/L2NzaWQvMS9jaWQvMTgwMzI0NTAvdC8w/dpuid/7361388378139043709/url/https://tag.crsspxl.com/m.gif?tnid=$!{TURN_UUID}"));

        assertEquals(
                "?",
                getMatch("https://tpc.googlesyndication.com/simgad/13369552527402012275?"));

        assertEquals(
                "#rand=0.43355788880912405&iit=1599404665798&tmr=load%3D1599404665735%26core%3D1599404665755%26main%3D1599404665796%26ifr%3D1599404665800&cb=0&cdn=0&md=0&kw=it%2Cbook%2Cebook%2Cfree%2Cdownload%2Clibrary%2Clib%2Cbooks%2Cebooks%2Cread%2Conline%2Cpdf%2Cdirect&ab=-&dh=it-ebooks.info&dr=&du=https%3A%2F%2Fit-ebooks.info%2Fpublisher%2F3%2Fpage%2F1%2F&href=https%3A%2F%2Fit-ebooks.info%2Fpublisher%2F3%2Fpage%2F1%2F&dt=O'Reilly%20Media%20eBooks%20Free%20Download&dbg=0&cap=tc%3D0%26ab%3D0&inst=1&jsl=33&prod=undefined&lng=en&ogt=&pc=men&pub=ra-4e54e03156d0c0e9&ssl=1&sid=5f54fa79a8098988&srf=0.01&ver=300&xck=0&xtr=0&og=&csi=undefined&rev=v8.28.7-wp&ct=1&xld=1&xd=1",
                getMatch("https://s7.addthis.com/static/sh.f48a1a04fe8dbf021b4cda1d.html#rand=0.43355788880912405&iit=1599404665798&tmr=load%3D1599404665735%26core%3D1599404665755%26main%3D1599404665796%26ifr%3D1599404665800&cb=0&cdn=0&md=0&kw=it%2Cbook%2Cebook%2Cfree%2Cdownload%2Clibrary%2Clib%2Cbooks%2Cebooks%2Cread%2Conline%2Cpdf%2Cdirect&ab=-&dh=it-ebooks.info&dr=&du=https%3A%2F%2Fit-ebooks.info%2Fpublisher%2F3%2Fpage%2F1%2F&href=https%3A%2F%2Fit-ebooks.info%2Fpublisher%2F3%2Fpage%2F1%2F&dt=O'Reilly%20Media%20eBooks%20Free%20Download&dbg=0&cap=tc%3D0%26ab%3D0&inst=1&jsl=33&prod=undefined&lng=en&ogt=&pc=men&pub=ra-4e54e03156d0c0e9&ssl=1&sid=5f54fa79a8098988&srf=0.01&ver=300&xck=0&xtr=0&og=&csi=undefined&rev=v8.28.7-wp&ct=1&xld=1&xd=1"));

        assertEquals(
                "#dnt=false&id=twitter-widget-0&lang=en&screen_name=JeromeKehrli&show_count=false&show_screen_name=true&size=m&time=1598897204857",
                getMatch("https://platform.twitter.com/widgets/follow_button.3c5aa8e2a38bbbee4b6d88e6846fc657.en.html#dnt=false&id=twitter-widget-0&lang=en&screen_name=JeromeKehrli&show_count=false&show_screen_name=true&size=m&time=1598897204857"));

        assertEquals(
                "?topUrl=aixtrem-airsoft.forumactif.com#{\"optout\":{\"value\":false,\"origin\":0},\"uid\":{\"origin\":0},\"sid\":{\"origin\":0},\"origin\":\"publishertag\",\"version\":99,\"lwid\":{\"origin\":0},\"tld\":\"forumactif.com\",\"bundle\":{\"origin\":0},\"topUrl\":\"aixtrem-airsoft.forumactif.com\",\"cw\":true,\"ifa\":{\"origin\":0}}",
                getMatch("https://gum.criteo.com/syncframe?topUrl=aixtrem-airsoft.forumactif.com#{\"optout\":{\"value\":false,\"origin\":0},\"uid\":{\"origin\":0},\"sid\":{\"origin\":0},\"origin\":\"publishertag\",\"version\":99,\"lwid\":{\"origin\":0},\"tld\":\"forumactif.com\",\"bundle\":{\"origin\":0},\"topUrl\":\"aixtrem-airsoft.forumactif.com\",\"cw\":true,\"ifa\":{\"origin\":0}}"));

        assertEquals(
                "?UISTB=<taboolaUserId>&us_privacy=1---&redir=https%3A%2F%2Fsync-t1.taboola.com%2Fsg%2Ftelaria-rtb-network%2F1%2Frtb-h%2F%3Fgdpr%3D0%26us_privacy%3D1---%26taboola_hm%3D%5BTVUSER_ID%5D%26orig%3Dvideo",
                getMatch("https://taboola-supply-partners.tremorhub.com/sync?UISTB=<taboolaUserId>&us_privacy=1---&redir=https%3A%2F%2Fsync-t1.taboola.com%2Fsg%2Ftelaria-rtb-network%2F1%2Frtb-h%2F%3Fgdpr%3D0%26us_privacy%3D1---%26taboola_hm%3D%5BTVUSER_ID%5D%26orig%3Dvideo"));

        assertEquals(
                "?r=3&p=4&cp=pubmaticUS&cu=1&&gdpr=0&gdpr_consent=&url=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTE5MjgmdGw9NDMyMDA=&piggybackCookie=uid:@@CRITEO_USERID@@",
                getMatch("https://dis.criteo.com/dis/usersync.aspx?r=3&p=4&cp=pubmaticUS&cu=1&&gdpr=0&gdpr_consent=&url=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTE5MjgmdGw9NDMyMDA=&piggybackCookie=uid:@@CRITEO_USERID@@"));

        assertEquals(
                null,
                getMatch("https://sync.1rx.io/usersync2/pubmatic&gdpr=0&gdpr_consent="));

        assertEquals(
                "?r=3&p=4&cp=pubmaticUS&cu=1&&gdpr=0&gdpr_consent=&url=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTE5MjgmdGw9NDMyMDA=&piggybackCookie=uid:@@CRITEO_USERID@@",
                getMatch("https://dis.criteo.com/dis/usersync.aspx?r=3&p=4&cp=pubmaticUS&cu=1&&gdpr=0&gdpr_consent=&url=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTE5MjgmdGw9NDMyMDA=&piggybackCookie=uid:@@CRITEO_USERID@@"));

        assertEquals(
                null,
                getMatch("https://air-soft.gun-evasion.com/blog/wp-content/uploads/2016/11/Capture-d’écran-2016-11-16-à-17.18.09.png"));

    }

    private String getMatch(String s) {
        Matcher matcher = HttpClient.requestValidationPattern.matcher(s);
        assertTrue (matcher.matches());
        return matcher.group(9);
    }

    @Test
    public void testEnsureEscaping() {

        // reference https://www.w3schools.com/tags/ref_urlencode.ASP

        assertEquals (
                "/serving/cookie/match?party=14&redirect=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTI4NzUmdGw9NDMyMDA=&piggybackCookie=[PLACE%20YOUR%20PIGGYBACK%20COOKIES%20HERE]&gdpr=0&gdpr_consent=",
            URI.create(HttpClient.ensureEscaping(
                "/serving/cookie/match?party=14&redirect=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTI4NzUmdGw9NDMyMDA=&piggybackCookie=[PLACE YOUR PIGGYBACK COOKIES HERE]&gdpr=0&gdpr_consent=")).toString());

        assertEquals (
                "/i.match?p=b11&redirect=https%3A//simage2.pubmatic.com/AdServer/Pug%3Fvcode%3Dbz0yJnR5cGU9MSZjb2RlPTMzMjYmdGw9MTI5NjAw%26piggybackCookie%3D%24TF_USER_ID_ENC%24&u=$%7BPUBMATIC_UID%7D",
            URI.create(HttpClient.ensureEscaping(
                "/i.match?p=b11&redirect=https%3A//simage2.pubmatic.com/AdServer/Pug%3Fvcode%3Dbz0yJnR5cGU9MSZjb2RlPTMzMjYmdGw9MTI5NjAw%26piggybackCookie%3D%24TF_USER_ID_ENC%24&u=${PUBMATIC_UID}")).toString());

        assertEquals (
                "/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTIxODQmdGw9MTU3NjgwMA==&r=https://pixel.tapad.com/idsync/ex/receive?partner_id=PUBMATIC_RTB&partner_device_id=$%7BPUBMATIC_UID%7D",
            URI.create(HttpClient.ensureEscaping(
                "/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTIxODQmdGw9MTU3NjgwMA==&r=https://pixel.tapad.com/idsync/ex/receive?partner_id=PUBMATIC_RTB&partner_device_id=${PUBMATIC_UID}")).toString());

        assertEquals (
                "/bh/rtset?pid=557219&ev=1&rurl=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZqcz0xJmNvZGU9MzMxOSZ0bD0xMjk2MDA=&ev=1&piggybackCookie=%25%25VGUID%25%25",
            URI.create(HttpClient.ensureEscaping(
                "/bh/rtset?pid=557219&ev=1&rurl=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZqcz0xJmNvZGU9MzMxOSZ0bD0xMjk2MDA=&ev=1&piggybackCookie=%%VGUID%%")).toString());

        assertEquals (
                "/syncframe?topUrl=www.brack.ch#%7B%22bundle%22:%7B%22origin%22:0,%22value%22:null%7D,%22cw%22:true,%22lwid%22:%7B%22origin%22:0,%22value%22:null%7D,%22optout%22:%7B%22origin%22:0,%22value%22:null%7D,%22origin%22:%22onetag%22,%22pm%22:0,%22sid%22:%7B%22origin%22:0,%22value%22:null%7D,%22tld%22:%22brack.ch%22,%22topUrl%22:%22www.brack.ch%22,%22uid%22:null,%22version%22:%225_6_2%22%7D",
            URI.create(HttpClient.ensureEscaping(
                "/syncframe?topUrl=www.brack.ch#{\"bundle\":{\"origin\":0,\"value\":null},\"cw\":true,\"lwid\":{\"origin\":0,\"value\":null},\"optout\":{\"origin\":0,\"value\":null},\"origin\":\"onetag\",\"pm\":0,\"sid\":{\"origin\":0,\"value\":null},\"tld\":\"brack.ch\",\"topUrl\":\"www.brack.ch\",\"uid\":null,\"version\":\"5_6_2\"}")).toString());

        assertEquals (
                "sync?UISTB=%3CtaboolaUserId%3E&us_privacy=1---&redir=https%3A%2F%2Fsync-t1.taboola.com%2Fsg%2Ftelaria-rtb-network%2F1%2Frtb-h%2F%3Fgdpr%3D0%26us_privacy%3D1---%26taboola_hm%3D%5BTVUSER_ID%5D%26orig%3Dvideo",
            URI.create(HttpClient.ensureEscaping(
                "sync?UISTB=<taboolaUserId>&us_privacy=1---&redir=https%3A%2F%2Fsync-t1.taboola.com%2Fsg%2Ftelaria-rtb-network%2F1%2Frtb-h%2F%3Fgdpr%3D0%26us_privacy%3D1---%26taboola_hm%3D%5BTVUSER_ID%5D%26orig%3Dvideo")).toString());

        assertEquals (
                "/i.match?p=b11&redirect=https%3A//simage2.pubmatic.com/AdServer/Pug%3Fvcode%3Dbz0yJnR5cGU9MSZjb2RlPTMzMjYmdGw9MTI5NjAw%26piggybackCookie%3D%24TF_USER_ID_ENC%24&u=$%7BPUBMATIC_UID%7D",
            URI.create(HttpClient.ensureEscaping(
                "/i.match?p=b11&redirect=https%3A//simage2.pubmatic.com/AdServer/Pug%3Fvcode%3Dbz0yJnR5cGU9MSZjb2RlPTMzMjYmdGw9MTI5NjAw%26piggybackCookie%3D%24TF_USER_ID_ENC%24&u=${PUBMATIC_UID}")).toString());

        assertEquals (
                "/bh/rtset?pid=557219&ev=1&rurl=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZqcz0xJmNvZGU9MzMxOSZ0bD0xMjk2MDA=&ev=1&piggybackCookie=%25%25VGUID%25%25",
            URI.create(HttpClient.ensureEscaping(
                "/bh/rtset?pid=557219&ev=1&rurl=https://simage2.pubmatic.com/AdServer/Pug?vcode=bz0yJnR5cGU9MSZqcz0xJmNvZGU9MzMxOSZ0bD0xMjk2MDA=&ev=1&piggybackCookie=%%VGUID%%")).toString());

        assertEquals (
                "/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTIxODQmdGw9MTU3NjgwMA==&r=https://pixel.tapad.com/idsync/ex/receive?partner_id=PUBMATIC_RTB&partner_device_id=$%7BPUBMATIC_UID%7D",
            URI.create(HttpClient.ensureEscaping(
                "/AdServer/Pug?vcode=bz0yJnR5cGU9MSZjb2RlPTIxODQmdGw9MTU3NjgwMA==&r=https://pixel.tapad.com/idsync/ex/receive?partner_id=PUBMATIC_RTB&partner_device_id=${PUBMATIC_UID}")).toString());

        assertEquals (
                "/sync/img?mt_exid=5&redir=https%3A%2F%2Feu-u.openx.net%2Fw%2F1.0%2Fsd%3Fid%3D536872786%26val%3D%5BMM_UUID%5D",
            URI.create(HttpClient.ensureEscaping(
                "/sync/img?mt_exid=5&redir=https%3A%2F%2Feu-u.openx.net%2Fw%2F1.0%2Fsd%3Fid%3D536872786%26val%3D%5BMM_UUID%5D")).toString());

        assertEquals (
                "/track/cmf/openx?oxid=ec0cd287-3058-3a63-70fd-0e4655ad9e49&gdpr=0",
            URI.create(HttpClient.ensureEscaping(
                "/track/cmf/openx?oxid=ec0cd287-3058-3a63-70fd-0e4655ad9e49&gdpr=0 ")).toString());

        assertEquals (
                "/track/cmf/openx?oxid=ec0cd287-3058-3a63-70fd-0e4655ad9e49&gdpr=0",
                URI.create(HttpClient.ensureEscaping(
                        "/track/cmf/openx?oxid=ec0cd287-3058-3a63-70fd-0e4655ad9e49&gdpr=0\n")).toString());

        assertEquals (
                "/track/cmf/openx?oxid=ec0cd287-3058-3a63-70fd-0e4655ad9e49&gdpr=0",
                URI.create(HttpClient.ensureEscaping(
                        "/track/cmf/openx?oxid=ec0cd287-3058-3a63-70fd-0e4655ad9e49&gdpr=0\t")).toString());

        assertEquals (
                "/w/1.0/cm?_=%7BCACHEBUSTER%7D&id=47f31213-389c-4904-aaa6-9b11aab9c211&gdpr=&gdpr_consent=&us_privacy=&r=https%3A%2F%2Frtb.gumgum.com%2Fusersync%3Fb%3Dopx%26i%3D",
            URI.create(HttpClient.ensureEscaping(
                "/w/1.0/cm?_={CACHEBUSTER}&id=47f31213-389c-4904-aaa6-9b11aab9c211&gdpr=&gdpr_consent=&us_privacy=&r=https%3A%2F%2Frtb.gumgum.com%2Fusersync%3Fb%3Dopx%26i%3D")).toString());

        assertEquals (
                "/r/dd/id/L2NzaWQvMS9jaWQvMTgwMzI0NTAvdC8w/dpuid/2636305136083781092/url/https://tag.crsspxl.com/m.gif?tnid=$!%7BTURN_UUID%7D",
            URI.create(HttpClient.ensureEscaping(
                "/r/dd/id/L2NzaWQvMS9jaWQvMTgwMzI0NTAvdC8w/dpuid/2636305136083781092/url/https://tag.crsspxl.com/m.gif?tnid=$!{TURN_UUID}")).toString());

        assertEquals ("/skins/4534433/fonts/bootstrap/glyphicons-halflings-regular%25eot",
            URI.create(HttpClient.ensureEscaping(
                "/skins/4534433/fonts/bootstrap/glyphicons-halflings-regular%eot")).toString());

        assertEquals ("https://air-soft.gun-evasion.com/blog/wp-content/uploads/2016/11/Capture-d’écran-2016-11-16-à-17.18.09.png",
                URI.create(HttpClient.ensureEscaping(
                "https://air-soft.gun-evasion.com/blog/wp-content/uploads/2016/11/Capture-d’écran-2016-11-16-à-17.18.09.png")).toString());

    }
}
