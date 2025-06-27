# Simple-Distributed-System

<br>

## HW 개요
Simple Distributed System은 Primary Storage와 Local Storage 간의 CRUD 작업 및 동기화를 <br>
HTTP 기반으로 수행하며, Primary-based Remote-Write 방식을 사용합니다. 각 서버(API, TCP, UDP)는 <br> 
시작 시 별도의 Local Storage를 생성하고, config.txt를 참고해 사용 가능한 포트를 랜덤으로 할당받아  <br>
독립적으로 동작합니다. API는 HTTP 요청, TCP/UDP는 클라이언트를 통해 데이터 작업을 처리합니다. <br>

<br>

### 주요 특징 및 설계 요소
- [x] 사용하지 않는 포트를 동적으로 할당하기 위해 Config.txt 파일 참조
- [x] Java NIO 기반 비동기 네트워크 처리
- [x] TCP/HTTP 프로토콜 요청을 직접 파싱하여 처리
- [x] 각 서버가 독립적인 Local Storage 프로세스를 실행 시 자동 생성
- [x] 동시성 제어를 위해 ReentrantLock으로 데이터 저장 보호

<br>

## **시퀸스 다이어그램**
**[ Primary Server and Start ( API / TCP / UDP ) Server ]** <br>
![image](https://github.com/user-attachments/assets/c855a022-83e7-4f67-8c44-549e4fe3b4d6)
1) 사용자에게 애플리케이션 이름과 포트 번호를 입력받아 서버를 생성 <br>
API / TCP / UDP 서버 중 선택하여 프로세스 실행 <br>
2) 서버가 실행되면 독립적인 Local Storage 프로세스를 생성 <br>
이때 인자로 App Name / 서버의 IP 주소 / 사용가능한 포트를 전달 <br>
3) Local Storage 프로세스가 실행되면 Http 요청으로 Primary Server에게 데이터 초기화를 요청 <br>
4) Primary Server는 초기화 요청을 받을 경우 요청을 보낸 Local Storage의 포트를 <br>
리스트에 저장하고 Primary Server가 가지고 있는 데이터 리스트를 응답으로 전달 <br>
5) Local Storage는 응답으로 받은 데이터 리스트를 자신의 Data Storage에 초기화 <br>

<br>

**※ 프로토콜 및 서버들의 통신 규약 정리** <br>
Primary Server ⇄ Local Storage ⇒ HTTP 통신 <br>
API Server ⇄ Local Storage ⇒ HTTP 통신 <br>
TCP Server ⇄ Local Storage ⇒ TCP 프로토콜 통신 <br> 
UDP Server ⇄ Local Storage ⇒ UDP 프로토콜 통신 <br>
Client ⇄ API / TCP / UDP Server ⇒ Client의 선택에 따라 프로토콜에 맞춰서 통신 <br>
Local Storage는 TCP, UDP 통신을 모두 진행하기 위해 2개의 Server Channel 및 Http Parsing 작업진행 <br>

<br>

**[ Client Request to API Server ]** <br>
![image](https://github.com/user-attachments/assets/886ee32e-5084-42bd-9db7-f51d2f72b403)
1) Client Process에서 Http Request를 생성하고, 자신과 연결된 API Server에게 요청 <br>
2) API Server는 자신의 Local Storage에게 해당 요청을 전달 <br>
3) Local Storage에서 Http 요청을 파싱한 후 만약 요청이 read라면 응답을 생성하여 전달 <br>
4) Local Storage에서 Http 요청을 파싱한 후 만약 요청이 write라면 <br>
응답을 생성하는 대신 Primary Server에게 요청을 forward 진행<br>
5) Primary-based Remote-Write Algorithm을 통해 모든 Local Storage에게 <br> 
Write Operation을 backup 시키고 데이터 동기화 진행. <br>
6) 모든 동기화 작업이 완료될 경우 Local Storage는 최종 응답을 API Server에게 전달 <br>
7) API Server는 최종 응답을 Client Process에게 전달 <br>

<br>

**[ Primary-based Remote-Write Algorithm ]** <br>
![image](https://github.com/user-attachments/assets/ac0b98fc-6d9c-4b7c-93d6-33143ad59d60)

<br>

## **아키텍처 다이어그램**
![image](https://github.com/user-attachments/assets/3b011261-04e9-44fa-afda-421d71e9564d)

<br>

**[ OS Ubuntu 22.04 LTS ]** <br>
이 시스템의 운영 체제로 해당 시스템에서 모든 프로그램이 실행되는 기본 환경을 제공 <br>

**[ JVM (Java Virtual Machine) ]** <br>
운영 체제 위에서 실행되며, 자바 바이트코드를 실행하여 애플리케이션이 운영 체제에서 동작 <br>

**[ Gson Jar File ]** <br>
JSON 데이터를 자바 객체로 변환하거나 그 반대로 변환하기 위한 라이브러리인 Gson의 JAR 파일 <br>
해당 애플리케이션에서는 JSON 데이터를 직렬화하거나 역직렬화하는 데 사용 <br>

**[ Config.txt ]** <br>
시스템 내에서 사용할 포트를 관리하고 할당하는 데 사용하는 텍스트 파일 <br>

**[ 6개의 자바 프로세스 ]** <br>
Primary Server 및 API / UDP / TCP Server 모두 독립적인 프로세스로 작동 <br>
API / UDP / TCP 서버가 시작되면 Local Storage 프로세스는 별도의 프로세스로 서버에서 자동으로 실행 <br>
Client Application을 통해 API / TCP / UDP 서버와 상호작용하며 테스트 진행 <br>

<br>

## **Directory**
```
📦Simple-Distributed-System
 ┣ 📂ApiServer
 ┃ ┗ 📂src
 ┃ ┃ ┣ 📂util
 ┃ ┃ ┃ ┣ 📜LoggingUtil.java
 ┃ ┃ ┃ ┣ 📜PortConfigType.java
 ┃ ┃ ┃ ┣ 📜PortUtil.java
 ┃ ┃ ┃ ┗ 📜RequestDto.java
 ┃ ┃ ┣ 📜APIServer.java
 ┃ ┃ ┗ 📜Main.java
 ┣ 📂Client
 ┃ ┗ 📂src
 ┃ ┃ ┗ 📜Client.java
 ┣ 📂LocalStorage
 ┃ ┗ 📂src
 ┃ ┃ ┣ 📂util
 ┃ ┃ ┃ ┣ 📜Data.java
 ┃ ┃ ┃ ┣ 📜DataUtil.java
 ┃ ┃ ┃ ┣ 📜HttpManager.java
 ┃ ┃ ┃ ┗ 📜RequestDto.java
 ┃ ┃ ┣ 📜LocalStorage.java
 ┃ ┃ ┗ 📜Main.java
 ┣ 📂PrimaryStorageServer
 ┃ ┗ 📂src
 ┃ ┃ ┣ 📜Data.java
 ┃ ┃ ┣ 📜DataStorage.java
 ┃ ┃ ┣ 📜LoggingUtil.java
 ┃ ┃ ┣ 📜Main.java
 ┃ ┃ ┣ 📜PrimaryStorage.java
 ┃ ┃ ┣ 📜RequestDto.java
 ┃ ┃ ┗ 📜ResponseDto.java
 ┣ 📂TcpServer
 ┃ ┗ 📂src
 ┃ ┃ ┣ 📂util
 ┃ ┃ ┃ ┣ 📜LoggingUtil.java
 ┃ ┃ ┃ ┣ 📜PortConfigType.java
 ┃ ┃ ┃ ┣ 📜PortUtil.java
 ┃ ┃ ┃ ┗ 📜RequestDto.java
 ┃ ┃ ┣ 📜Main.java
 ┃ ┃ ┗ 📜TCPServer.java
 ┣ 📂UdpServer
 ┃ ┗ 📂src
 ┃ ┃ ┣ 📂util
 ┃ ┃ ┃ ┣ 📜LoggingUtil.java
 ┃ ┃ ┃ ┣ 📜PortConfigType.java
 ┃ ┃ ┃ ┣ 📜PortUtil.java
 ┃ ┃ ┃ ┗ 📜RequestDto.java
 ┃ ┃ ┣ 📜Main.java
 ┃ ┃ ┗ 📜UDPServer.java
 ┣ 📜config.txt
 ┣ 📜gson-2.10.1.jar
 ┗ 📜setting.sh
```
<br>

## **Reasoning behind the implementation Strategy**
### **Config.txt File References**
Config.txt 파일은 시스템 내 포트 관리와 할당을 담당하는 설정 파일로, <br>
Local Storage 생성 시 이를 참조해 사용하지 않는 포트를 랜덤으로 할당함으로써 포트 충돌을 방지한다. <br>
파일에는 포트 번호, 프로토콜, 서버 종류 등의 정보가 포함되어 있으며, 서버와 클라이언트가 이를 통해 <br>
효율적으로 통신하고 적절한 연결을 형성할 수 있도록 돕는다. 서버나 Local Storage가 종료되면 해당 정보는 <br>
자동으로 파일에서 제거된다. <br>


<br>

### **TCP and HTTP Request Parsing**
TCP 및 HTTP 요청 파싱은 클라이언트와 서버 간 통신을 처리하는 핵심 기능으로, <br>
Java NIO의 ServerSocketChannel을 통해 비동기 방식으로 요청을 수신한다. <br>
API Server는 HTTP 요청을 직접 파싱해 메서드, 헤더, 바디를 분리하고 <br>
Content-Length를 기준으로 데이터를 처리하며, TCP Server는 실시간으로 수신된 <br>
데이터의 유형을 파악해 CRUD 연산을 수행하는 구조로 동작한다. <br>

<br>

### **Independent Creation Of Localstorage Process**
시스템은 API, TCP, UDP 서버가 시작될 때 각각의 Local Storage를 ProcessBuilder를 통해 <br>
별도의 프로세스로 생성한다. 이는 서버별로 독립된 포트와 환경을 구성해 모듈화와 확장성을 강화하며, <br>
각 서버가 전용 Local Storage와 통신할 수 있게 한다. 생성 시 config.txt를 참조해 <br>
포트 충돌을 방지하고, <Primary Server와 연결하여 초기화 요청을 수행한다. 프로세스 종료 시 <br>
자동으로 Local Storage도 종료되며, 로그 및 오류 관리를 위해 표준 입출력 스트림도 제어 가능하다. <br>

```Java
private void startLocalStorage() {
 try {
      ProcessBuilder runPb = new ProcessBuilder(
                    "java",
                    "-cp", "../LocalStorage/src:../gson-2.10.1.jar",
                    "Main",
                    applicationName,
                    "127.0.0.1",
                    String.valueOf(storagePort)
      );
      runPb.inheritIO();
      localStorageProcess = runPb.start();
      System.out.println("Local Storage started as a separate process.");
   } catch (IOException e) {
      System.err.println("Failed to start Local Storage: " + e.getMessage());
   }
}
```
<br>

### **Primary Server’s Data Storage use ReentrantLock**
Primary Server는 DataStorage 클래스를 통해 Local Storage 요청을 처리하며 CRUD 연산을 수행합니다. <br>
이 클래스는 싱글톤 패턴으로 설계되었고, 두 개의 ReentrantLock을 사용해 <br>
스레드 동기화와 데이터 무결성을 보장합니다. <br>

#### **InstanceLock**
클래스 인스턴스 생성 시 동시성 문제를 방지하기 위해 사용되며, <br>
getInstance() 메서드 내에서 이중 확인 잠금 패턴을 통해 싱글톤 인스턴스를 안전하게 생성합니다. <br>

#### **MethodLock**
데이터 리스트에 대한 CRUD 작업 시, 여러 스레드가 동시에 접근하지 못하도록 메서드 수준에서 <br>
락을 적용합니다. save(), update(), delete() 등 주요 연산에서 이 락을 통해 데이터 무결성을 유지합니다. <br>

<br>

## **Settings**
### Ubuntu 기본 환경 구축
#### 사용자 홈 디렉토리로 이동 및 패키지 목록 업데이트
```Bash
cd ~
sudo apt update
```
#### JDK 21 다운로드 및 설정
```Bash
﻿wget https://download.oracle.com/java/21/archive/jdk-21_linux-x64_bin.tar.gz
﻿tar -xzf jdk-21_linux-x64_bin.tar.gz
﻿sudo mv jdk-21 /usr/local/
﻿nano ~/.bashrc
```
﻿※ 파일의 맨 아래에 다음 내용을 추가
```Bash
﻿export JAVA_HOME=/usr/local/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
```
※ 변경 사항 적용
```Bash
source ~/.bashrc
```
#### 프로젝트 clone 및 해당 프로젝트로 이동
```Bash
git clone https://github.com/ohchanKyu/HW-simple-distributed-system.git
cd ./HW-simple-distributed-system
```

<br>

### 애플리케이션 실행
#### setting.sh 파일 실행하여 모든 Java 파일 컴파일 
```Bash
sh setting.sh
```
#### PrimaryStorageServer 실행
```Bash
cd ./PrimaryStorageServer
java -cp ./src:../gson-2.10.1.jar Main
```
#### API Server 실행
```Bash
cd ./ApiServer
java -cp ./src:../gson-2.10.1.jar Main ApiDemo 8000
```
#### TCP Server 실행
```Bash
cd ./TcpServer
java -cp ./src:../gson-2.10.1.jar Main TCPDemo 8081
```
#### UDP Server 실행
```Bash
cd ./UdpServer
java -cp ./src:../gson-2.10.1.jar Main UdpDemo 8082
```
#### Client Process 실행
```Bash
cd ./Client
java -cp ./src:../gson-2.10.1.jar Client
```
<br>

## **Tested the performance of the assignment**
### **Primary-based Remote-Write 재해석**
![image](https://github.com/user-attachments/assets/8ca01ff2-eec7-42d6-934b-d19c6ae10595)

<br>

#### **Lazy Data Synchronization을 활용한 개선안 요약**
기존 시스템은 클라이언트가 Local Storage에 Write 요청을 보낸 후, <br>
모든 Local Storage에 데이터 동기화가 완료될 때까지 클라이언트 응답(W5)이 지연되는 구조로, <br>
특정 서버의 응답 지연이 전체 처리 속도에 영향을 주는 문제가 존재했습니다. <br>
이에 따라 아래와 같은 Lazy 동기화 방식이 개선안으로 제시됩니다. <br>

<br>

### **개선된 동작 흐름**
1. W1 (Write Request) <br>
클라이언트가 Local Storage에 데이터 저장 요청을 보냅니다. <br>

2. W2 (Forward Request) <br>
Local Storage는 Primary Server에 요청을 전달합니다. <br>

3. Primary Server 처리 <br>
데이터를 처리하고 결과를 Local Storage로 전달합니다. <br>

4. W5 (클라이언트 응답)
Local Storage는 응답을 받아 즉시 클라이언트에게 반환합니다. <br>
→ 동기화 이전에 클라이언트 응답 완료. <br>

5. W3 ~ W4 (백업 동기화) <br>
이후 Primary Server는 다른 Local Storage에 비동기적으로 동기화 작업을 수행합니다. <br>

<br>

### **주요 장점**
#### 빠른 응답 시간 확보
동기화 이전에 응답을 완료하여 클라이언트의 대기 시간을 최소화할 수 있습니다. <br>

#### 성능 최적화
동기화를 클라이언트 응답과 분리해 병렬 처리하므로, 전체 시스템 처리량이 향상됩니다. <br>

#### Eventually Consistent 구조 채택
동기화는 시간이 걸릴 수 있지만, 결국 모든 서버에 데이터가 반영되는 모델로 안정성도 유지합니다. <br>

<br>

### **[ 테스트 개요 ]**
알고리즘 변경에 따른 클라이언트 응답 시간 비교 테스트도 함께 진행되었습니다. <br>
Primary Server에 등록된 Local Storage의 수를 1, 5, 10, 20, 40개로 점진적으로 증가시키며, <br>
각 경우에 대한 응답 시간을 측정하였습니다. 기존 구조에서는 Local Storage 수가 많아질수록 <br>
백업 순서에 따라 응답 시간이 길어졌으나, 개선된 구조에서는 클라이언트 응답이 백업 동기화와 <br>
무관하게 선행되므로 성능 향상이 두드러지게 나타났습니다. <br>

<br>

### **[ 테스트 결과 ]**
![image](https://github.com/user-attachments/assets/b0855ce2-38a3-479c-8f69-1eb2e2c4633f)
