/*
    Copyright (c)  2006, 2007		Dmitry Butskoy
					<buc@citadel.stu.neva.ru>
    License:  GPL v2 or any later

    See COPYING for the status of this software.
*/

#include <netinet/in.h>
#include <stdbool.h>

#include "clif.h"

#define LOG_TAG "ANDROID-JNI"

#define RET_ERROR_NEGATIVE(cmd, err) { int ret = (cmd); if (ret < 0) return error(err); }
#define RET_NON_ZERO(cmd) { int ret = (cmd); if (ret) return ret; }

union common_sockaddr {
    struct sockaddr sa;
    struct sockaddr_in sin;
    struct sockaddr_in6 sin6;
};
typedef union common_sockaddr sockaddr_any;

struct probe_struct {
    int done;
    int final;
    sockaddr_any res;
    double send_time;
    double recv_time;
    int recv_ttl;
    int sk;
    int seq;
    char *ext;
    char err_str[16];    /*  assume enough   */
};
typedef struct probe_struct probe;


struct tr_module_struct {
    struct tr_module_struct *next;
    const char *name;

    int (*init)(const sockaddr_any *dest,
                unsigned int port_seq, size_t *packet_len);

    int (*send_probe)(probe *pb, int ttl);

    int (*recv_probe)(probe *probes, unsigned int num_probes, int fd, int revents);

    void (*expire_probe)(probe *pb);

    CLIF_option *options;    /*  per module options, if any   */
    int one_per_time;    /*  no simultaneous probes   */
    size_t header_len;    /*  additional header length (aka for udp)   */
};

typedef struct tr_module_struct tr_module;

#define __TEXT(X)       #X
#define _TEXT(X)        __TEXT(X)

#define DEF_START_PORT    33434    /*  start for traditional udp method   */
#define DEF_UDP_PORT    53    /*  dns   */
#define DEF_TCP_PORT    80    /*  web   */
#define DEF_DCCP_PORT    DEF_START_PORT    /*  is it a good choice?...  */
#define DEF_RAW_PROT    253    /*  for experimentation and testing, rfc3692  */
#define MAX_HOPS    255
#define MAX_PROBES    10


struct probe_result_struct {
    int ttl;
    char host[256];
    char ip[32];
    bool timeout;
    int recv_ttl;
    char ext[256];
    double delay;
    char err[16];
};
typedef struct probe_result_struct probe_result;

int error(const char *str);

double get_time(void);

int tune_socket(int sk);

void parse_icmp_res(probe *pb, int type, int code, int info);

void probe_done(probe *pb);

typedef probe *(*check_reply_t)(probe *probes, unsigned int num_probes, int sk, int err, sockaddr_any *from,
                                char *buf, size_t len);

int recv_reply(probe *probes, unsigned int num_probes, int sk, int err, check_reply_t check_reply);

int equal_addr(const sockaddr_any *a, const sockaddr_any *b);

probe *probe_by_seq(probe *probes, unsigned int num_probes, int seq);

probe *probe_by_sk(probe *probes, unsigned int num_probes, int sk);

int bind_socket(int sk);

void use_timestamp(int sk);

void use_recv_ttl(int sk);

int use_recverr(int sk);

int set_ttl(int sk, int ttl);

int do_send(int sk, const void *data, size_t len, const sockaddr_any *addr);

int add_poll(int fd, int events);

void del_poll(int fd);

int do_poll(probe *probes, unsigned int num_probes, double timeout, void (*callback)(probe *probes, unsigned int num_probes, int fd, int revents));

void handle_extensions(probe *pb, char *buf, int len, int step);

const char *get_as_path(const char *query);

int raw_can_connect(void);

unsigned int random_seq(void);

u_int16_t in_csum(const void *ptr, size_t len);


void tr_register_module(tr_module *module);

const tr_module *tr_get_module(const char *name);

#define TR_MODULE(MOD)    \
static void __init_ ## MOD (void) __attribute__ ((constructor));    \
static void __init_ ## MOD (void) {    \
                \
    tr_register_module (&MOD);    \
}

int traceroute(int argc, char *argv[], int *count, probe_result **probe_results, char *dst_addr_name);
