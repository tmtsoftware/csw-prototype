USR_DIR    =/usr/local
INCS_DIRS  =-I${USR_DIR}/include
LIBS_DIRS  =-L${USR_DIR}/lib 
#CPP_DEFS   =-D=HAVE_CONFIG_H
CPP_OPTS   =-Wall -O2
#LIBS       =-lczmq -lzmq
LIBS       =-lzmq.1


all: mtserver2

mtserver2: mtserver2.c zhelpers2.h
	gcc ${CPP_OPTS} ${CPP_DEFS} -o mtserver2 ${INCS_DIRS} mtserver2.c ${LIBS_DIRS} ${LIBS}

clean:
	$(RM) mtserver2

