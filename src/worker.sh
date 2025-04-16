#!/bin/bash 

apt update
apt upgrade -y
apt install -y docker.io

docker swarm join --token $(cat /vagrant/swarm_token) $1:2377