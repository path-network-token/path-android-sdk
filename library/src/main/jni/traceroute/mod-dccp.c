/*
    Copyright (c)  2012		Samuel Jero <sj323707@ohio.edu>
    License:  GPL v2 or any later

    See COPYING for the status of this software.
*/

#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>
#include <linux/dccp.h>


#include "traceroute.h"


#define DEF_SERVICE_CODE    1885957735

#define DCCP_HEADER_LEN        (sizeof (struct dccp_hdr) + \
                 sizeof (struct dccp_hdr_ext) \
                 + sizeof (struct dccp_hdr_request))

static sockaddr_any dest_addr = {{0,},};
static unsigned int dest_port = 0;

static int raw_sk = -1;
static int last_ttl = 0;

static u_int8_t buf[1024];    /*  enough, enough...  */
static size_t csum_len = 0;
static struct dccp_hdr *dh = NULL;
static struct dccp_hdr_ext *dhe = NULL;
static struct dccp_hdr_request *dhr = NULL;
static unsigned int service_code = DEF_SERVICE_CODE;


static CLIF_option dccp_options[] = {
        {0, "service", "NUM", "Set DCCP service code to %s (default is "
                              _TEXT (DEF_SERVICE_CODE) ")",
         CLIF_set_uint, &service_code, 0, CLIF_ABBREV},
        CLIF_END_OPTION
};


static int dccp_init(const sockaddr_any *dest,
                     unsigned int port_seq, size_t *packet_len_p) {
    int af = dest->sa.sa_family;
    sockaddr_any src;
    socklen_t len;
    u_int8_t *ptr;
    u_int16_t *lenp;


    dest_addr = *dest;
    dest_addr.sin.sin_port = 0;    /*  raw sockets can be confused   */

    if (!port_seq) port_seq = DEF_DCCP_PORT;
    dest_port = htons (port_seq);


    /*  Create raw socket for DCCP   */
    RET_ERROR_NEGATIVE(raw_sk = socket(af, SOCK_RAW, IPPROTO_DCCP), "socket");

    RET_NON_ZERO(tune_socket(raw_sk));        /*  including bind, if any   */

    RET_ERROR_NEGATIVE(connect(raw_sk, &dest_addr.sa, sizeof(dest_addr)), "connect");

    len = sizeof(src);
    RET_ERROR_NEGATIVE(getsockname(raw_sk, &src.sa, &len), "getsockname");

    if (!raw_can_connect()) {    /*  work-around for buggy kernels  */
        close(raw_sk);
        RET_ERROR_NEGATIVE(raw_sk = socket(af, SOCK_RAW, IPPROTO_DCCP), "socket");
        RET_NON_ZERO(tune_socket(raw_sk));
        /*  but do not connect it...  */
    }


    RET_NON_ZERO(use_recverr(raw_sk));

    RET_NON_ZERO(add_poll(raw_sk, POLLIN | POLLERR));

    /*  Now create the sample packet.  */

    /*  For easy checksum computing:
        saddr
        daddr
        length
        protocol
        dccphdr
    */

    ptr = buf;

    if (af == AF_INET) {
        len = sizeof(src.sin.sin_addr);
        memcpy(ptr, &src.sin.sin_addr, len);
        ptr += len;
        memcpy(ptr, &dest_addr.sin.sin_addr, len);
        ptr += len;
    } else {
        len = sizeof(src.sin6.sin6_addr);
        memcpy(ptr, &src.sin6.sin6_addr, len);
        ptr += len;
        memcpy(ptr, &dest_addr.sin6.sin6_addr, len);
        ptr += len;
    }

    lenp = (u_int16_t *) ptr;
    ptr += sizeof(u_int16_t);
    *((u_int16_t *) ptr) = htons ((u_int16_t) IPPROTO_DCCP);
    ptr += sizeof(u_int16_t);


    /*  Construct DCCP header   */
    dh = (struct dccp_hdr *) ptr;

    dh->dccph_ccval = 0;
    dh->dccph_checksum = 0;
    dh->dccph_cscov = 0;
    dh->dccph_dport = dest_port;
    dh->dccph_reserved = 0;
    dh->dccph_sport = 0;    /*  temporary   */
    dh->dccph_x = 1;
    dh->dccph_type = DCCP_PKT_REQUEST;
    dh->dccph_seq2 = 0;    /*  reserved if using 48 bit sequence numbers  */
    /*  high 16 bits of sequence number. Always make 0 for simplicity.  */
    dh->dccph_seq = 0;
    ptr += sizeof(struct dccp_hdr);

    dhe = (struct dccp_hdr_ext *) ptr;
    dhe->dccph_seq_low = 0;        /*  temporary   */
    ptr += sizeof(struct dccp_hdr_ext);

    dhr = (struct dccp_hdr_request *) ptr;
    dhr->dccph_req_service = htonl (service_code);
    ptr += sizeof(struct dccp_hdr_request);


    csum_len = ptr - buf;

    if (csum_len > sizeof(buf))
        return error("impossible");    /*  paranoia   */

    len = ptr - (u_int8_t *) dh;
    if (len & 0x03) return error("impossible");  /*  as >>2 ...  */

    *lenp = htons (len);
    dh->dccph_doff = len >> 2;


    *packet_len_p = len;

    return 0;
}


static int dccp_send_probe(probe *pb, int ttl) {
    int sk;
    int af = dest_addr.sa.sa_family;
    sockaddr_any addr;
    socklen_t len = sizeof(addr);


    /*  To make sure we have chosen a free unused "source port",
       just create, (auto)bind and hold a socket while the port is needed.
    */

    RET_ERROR_NEGATIVE(sk = socket(af, SOCK_DCCP, IPPROTO_DCCP), "socket");

    RET_NON_ZERO(bind_socket(sk));

    RET_ERROR_NEGATIVE(getsockname(sk, &addr.sa, &len), "getsockname");

    /*  When we reach the target host, it can send us either Reset or Response.
      For Reset all is OK (we and kernel just answer nothing), but
      for Response we should reply with our Close.
        It is well-known "half-open technique", used by port scanners etc.
      This way we do not touch remote applications at all, unlike
      the ordinary connect(2) call.
        As the port-holding socket neither connect() nor listen(),
      it means "no such port yet" for remote ends, and kernel always
      send Reset in such a situation automatically (we have to do nothing).
    */


    dh->dccph_sport = addr.sin.sin_port;

    dhe->dccph_seq_low = random_seq();

    dh->dccph_checksum = 0;
    dh->dccph_checksum = in_csum(buf, csum_len);


    if (ttl != last_ttl) {
        RET_NON_ZERO(set_ttl(raw_sk, ttl));
        last_ttl = ttl;
    }


    pb->send_time = get_time();

    int ret = do_send(raw_sk, dh, dh->dccph_doff << 2, &dest_addr);
    if (ret < 0) {
        close(sk);
        pb->send_time = 0;
        return ret;
    }


    pb->seq = dh->dccph_sport;

    pb->sk = sk;

    return 0;
}


static probe *dccp_check_reply(probe *probes, int sk, int err, sockaddr_any *from,
                               char *buf, size_t len) {
    probe *pb;
    struct dccp_hdr *ndh = (struct dccp_hdr *) buf;
    u_int16_t sport, dport;


    if (len < 8) return NULL;        /*  too short   */


    if (err) {
        sport = ndh->dccph_sport;
        dport = ndh->dccph_dport;
    } else {
        sport = ndh->dccph_dport;
        dport = ndh->dccph_sport;
    }


    if (dport != dest_port)
        return NULL;

    if (!equal_addr(&dest_addr, from))
        return NULL;

    pb = probe_by_seq(probes, sport);
    if (!pb) return NULL;

    if (!err) pb->final = 1;

    return pb;
}


static int dccp_recv_probe(probe *probes, int sk, int revents) {

    if (!(revents & (POLLIN | POLLERR)))
        return 0;

    return recv_reply(probes, sk, !!(revents & POLLERR), dccp_check_reply);
}


static void dccp_expire_probe(probe *pb) {

    probe_done(pb);
}


static tr_module dccp_ops = {
        .name = "dccp",
        .init = dccp_init,
        .send_probe = dccp_send_probe,
        .recv_probe = dccp_recv_probe,
        .expire_probe = dccp_expire_probe,
        .one_per_time = 0,
        .options = dccp_options,
};

TR_MODULE (dccp_ops);
