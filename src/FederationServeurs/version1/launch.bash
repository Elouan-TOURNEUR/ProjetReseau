#!/bin/bash


fichier="../../../../../src/pairs.cfg"
oldIFS=$IFS
IFS=$'\r\n' # séparateur de champs (\n ou \r\n)

cd ../../../out/production/Projet_Reseaux/FederationServeurs/version1
master="ChatamuCentral"
peer="SlaveServeur"
counter=1

for ligne in $(<$fichier)
do

serv=$ligne
serv=${serv%\ =*}

tmp=${ligne#*=\ }
adr=${tmp%\ *}
port=${tmp#*\ }

if [ $serv = "master" ]
then
echo "java $master $port"
java $master $port &
else
name="peer"
name="${name}${counter}"
echo "java $peer $adr $port $name"
java  $peer $adr $port $name &
counter=$((counter+1));
fi

done
