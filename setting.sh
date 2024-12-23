#!/bin/sh
sed -i 's/\r//' setting.sh
find ./ApiServer/src -name "*.java" > sources
javac -cp gson-2.10.1.jar -d ./ApiServer/src @sources
find ./Client/src -name "*.java" > sources
javac -cp gson-2.10.1.jar -d ./Client/src @sources
find ./LocalStorage/src -name "*.java" > sources
javac -cp gson-2.10.1.jar -d ./LocalStorage/src @sources
find ./PrimaryStorageServer/src -name "*.java" > sources
javac -cp gson-2.10.1.jar -d ./PrimaryStorageServer/src @sources
find ./TcpServer/src -name "*.java" > sources
javac -cp gson-2.10.1.jar -d ./TcpServer/src @sources
find ./UdpServer/src -name "*.java" > sources
javac -cp gson-2.10.1.jar -d ./UdpServer/src @sources
rm sources
