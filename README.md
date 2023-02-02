# ESP32-for-CLPAT
ESP32 DevKit 보드를 이용하여 탑승자 승차권 식별기 제작

# 폴더 설명
<pre style="margin-left=50px">
 1. Android-DriverApp<br>
    가. OS       : 안드로이드<br>
    나. Language : java<br>
    다. OverView : 운전자 앱으로서 아래와 같은 주요 기능을 실행한다.<br>
        1) 승차권 식별기와 운전자 스마트폰과 블루투스로 통신한다.<br>
        2) 승차권 식별기는 QR-Reader로 부터 읽은 승차권 데이터를 암호화하여 운전자 앱으로 송신한다.<br>
        3) 운전자 앱은 수신한 승차권 데이터를 복호화하고 인증 작업을 실시한다.<br>
        4) 인증 작업의 결과를 TTS를 이용, 탑승자에게 안내하고 원격 서버로 전송한다.<br>
        5) 일정 주기로 수집한 GPS 데이터를 원격 서버로 전송한다.<br>
 2. Arduino-ESP32<br></pre>
