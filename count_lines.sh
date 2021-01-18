#!/usr/bin/env bash

(for i in `find ./src/main/java`; do if [[ ! -L $i &&  ! -d $i && `echo $i | grep jquery` == "" && `echo $i | grep bootstrap` == "" ]]; then cat $i; fi; done) | wc -l > /tmp/java_lc
(for i in `find ./src/main/webapp`; do if [[ ! -L $i &&  ! -d $i && `echo $i | grep jquery` == "" && `echo $i | grep bootstrap` == "" && `echo $i | grep present` == ""  && `echo $i | grep img` == "" && `echo $i | grep masonry` == ""  && `echo $i | grep font` == ""  && `echo $i | grep vendor` == "" ]]; then cat $i; fi; done)  | wc -l > /tmp/web_lc
(for i in `find ./src/test`; do if [[ ! -L $i && ! -d $i ]]; then cat $i; fi; done) | wc -l > /tmp/test_lc

echo "Java LC : `cat /tmp/java_lc`"
echo "Web  LC : `cat /tmp/web_lc`"
echo "Test LC : `cat /tmp/test_lc`"

rm /tmp/java_lc
rm /tmp/web_lc
rm /tmp/test_lc
