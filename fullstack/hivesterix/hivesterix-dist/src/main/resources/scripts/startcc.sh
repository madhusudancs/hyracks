#!/bin/bash
hostname

#Import cluster properties
. conf/cluster.properties

#Get the IP address of the cc
CCHOST_NAME=`cat conf/master`
CCHOST=`bin/getip.sh`

#Remove the temp dir
rm -rf $CCTMP_DIR
mkdir $CCTMP_DIR

#Remove the logs dir
rm -rf $CCLOGS_DIR
mkdir $CCLOGS_DIR

#Export JAVA_HOME and JAVA_OPTS
export JAVA_HOME=$JAVA_HOME
export JAVA_OPTS=$CCJAVA_OPTS

#Launch hyracks cc script
chmod -R 755 $HYRACKS_HOME
if [ -f "conf/topology.xml"  ]; then
#Launch hyracks cc script with topology
$HYRACKS_HOME/hyracks-server/target/appassembler/bin/hyrackscc -client-net-ip-address $CCHOST -cluster-net-ip-address $CCHOST -client-net-port $CC_CLIENTPORT -cluster-net-port $CC_CLUSTERPORT -max-heartbeat-lapse-periods 999999 -default-max-job-attempts 0 -job-history-size 0 -cluster-topology "conf/topology.xml" &> $CCLOGS_DIR/cc.log &
else
#Launch hyracks cc script without toplogy
$HYRACKS_HOME/hyracks-server/target/appassembler/bin/hyrackscc -client-net-ip-address $CCHOST -cluster-net-ip-address $CCHOST -client-net-port $CC_CLIENTPORT -cluster-net-port $CC_CLUSTERPORT -max-heartbeat-lapse-periods 999999 -default-max-job-attempts 0 -job-history-size 0 &> $CCLOGS_DIR/cc.log &
fi
