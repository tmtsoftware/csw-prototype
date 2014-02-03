//
//  Multithreaded server (based on zeromq-2.2 example)
//
#include "zhelpers2.h"
#include <pthread.h>
#include <signal.h>

//  ---------------------------------------------------------------------
//  Signal handling
//
//  Call s_catch_signals() in your application at startup, and then exit
//  your main loop if s_interrupted is ever 1. Works especially well with
//  zmq_poll.

static int s_interrupted = 0;
static void s_signal_handler (int signal_value)
{
    s_interrupted = 1;
}

static void s_catch_signals (void)
{
    struct sigaction action;
    action.sa_handler = s_signal_handler;
    action.sa_flags = 0;
    sigemptyset (&action.sa_mask);
    sigaction (SIGINT, &action, NULL);
    sigaction (SIGTERM, &action, NULL);
}

static void *
worker_routine (void *context) {
    //  Socket to talk to dispatcher
    void *receiver = zmq_socket (context, ZMQ_REP);
    zmq_connect (receiver, "inproc://workers");

    while (!s_interrupted) {
        char *string = s_recv (receiver);
        if (string) {
            printf ("Received request: [%s]\n", string);
            //  Do some 'work'
            free (string);
	        srandomdev();
            int ms = 1000 * (randof(5000) + 1);
            printf ("This will take %g secs\n", ms/1000000.);
            usleep (ms);
            printf ("Done: Sending OK reply back to sender\n");
            //  Send reply back to client
            s_send (receiver, "OK");
        } else {
             break;
        }
    }
    if (s_interrupted) printf("Interrupted\n");
    zmq_close (receiver);
    return NULL;
}

int main (int argc, char** argv)
{
    if (argc != 2) {
	printf("Expected one argument: filter or disperser\n");
	exit(1);
    }
    // Use a different port depending on the argument (filter, disperser, pos, one)
    // Make sure this matches the values in resources/TestConfigActor.conf.
    // Later on, this should be read from a config file or service.
    char* url = "tcp://*:6565";
    if (strcmp(argv[1], "filter") == 0) {
        url = "tcp://*:6565";
    } else if (strcmp(argv[1], "disperser") == 0) {
        url = "tcp://*:6566";
    } else if (strcmp(argv[1], "pos") == 0) {
        url = "tcp://*:6567";
    } else if (strcmp(argv[1], "one") == 0) {
        url = "tcp://*:6568";
    }
    printf("Listening for %s commands on %s\n", argv[1], url);

    void *context = zmq_init (1);
    s_catch_signals ();

    //  Socket to talk to clients
    void *clients = zmq_socket (context, ZMQ_ROUTER);
    zmq_bind (clients, url);

    //  Socket to talk to workers
    void *workers = zmq_socket (context, ZMQ_DEALER);
    zmq_bind (workers, "inproc://workers");

    //  Launch pool of worker threads
    int thread_nbr;
    for (thread_nbr = 0; thread_nbr < 5; thread_nbr++) {
        pthread_t worker;
        pthread_create (&worker, NULL, worker_routine, context);
    }
    //  Connect work threads to client threads via a queue
    zmq_device (ZMQ_QUEUE, clients, workers);

    //  We never get here but clean up anyhow
    zmq_close (clients);
    zmq_close (workers);
    zmq_term (context);
    return 0;
}
