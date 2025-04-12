#!/bin/bash 

apt update
apt upgrade -y
apt install -y docker.io

mkdir /home/vagrant/workdir
cp -r /vagrant /home/vagrant/workdir/src

docker swarm init --advertise-addr $1
docker swarm join-token worker -q > /vagrant/swarm_token