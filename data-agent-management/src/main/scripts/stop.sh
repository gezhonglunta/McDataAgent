#!/bin/bash

pids=`ps -ef | grep data-agent.jar | grep port=8780 | grep -v grep | awk '{print $2}'`
if [[ -n $pids ]]; then
    for pid in ${pids}; do
        echo kill:$pid
        kill $pid
    done
    for pid in ${pids}; do
        count=0
        while kill -0 $pid 2>/dev/null; do
            if [[ $count -ge 30 ]]; then
                echo force-kill:$pid
                kill -9 $pid
                break
            fi
            sleep 1
            count=$((count + 1))
        done
        if ! kill -0 $pid 2>/dev/null; then
            echo stopped:$pid
        fi
    done
fi


