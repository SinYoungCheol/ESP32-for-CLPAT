// #include <HardwareSerial.h>
#include "BluetoothSerial.h"
#include "RGB.h"

#define SZ_RXBUF_QRD 256

BluetoothSerial SerialBT;
RGB rgb;

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
    #error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

char rxBufQrd[SZ_RXBUF_QRD] = {0};

// Bluetooth SPP에서의 이벤트 처리
void bt_callback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param) { 
    if (event == ESP_SPP_SRV_OPEN_EVT) {
        rgb.setColor(0, 255, 0); // green
        Serial.println("client connect");
    }
    if (event == ESP_SPP_CLOSE_EVT) {
        rgb.setColor(255, 0, 0); // red
        Serial.println("client disconnect");
    }
}

void setup()
{
    rgb.begin(25, 26, 27); // pin8,7,6 == GPIO 25, 26, 27 => [ Red=25, Green=26, Blue=27 ]
    rgb.setColor(0, 0, 255);

    // setup serial port for Debug
    Serial.begin(115200);
    Serial.println("connected Debugger.");
    
    // setup serial port for Bluetooth Centeral Device
    esp_err_t ret = SerialBT.register_callback(bt_callback);
    if (!SerialBT.begin("CLPAT-ESP32", false)) {
        Serial.println("An error occurred initializing Bluetooth");
    }
    else {
        Serial.println("Bluetooth initialized");
    }
    

    Serial.println("connected Bluetooth (CLPAT-ESP32).");
    // setup serial port for QR-Reader
    Serial2.begin(115200, SERIAL_8N1, 16, 17);
    Serial.println("connected QR-Reader.");
    delay(1000);

    memset(rxBufQrd, 0, SZ_RXBUF_QRD);
}

void loop()
{
    if (Serial2.available() > 0) // QR-Reader's UART
    {
        // QR-READER로 부터 읽은 자료의 마지막 내용은 \r\n 이다.
        // 아래의 readBytesUntil과 같이 '\n'을 검색하면 수신 내용 끝에 '\r'만 포함한다.
        size_t rxSize = Serial2.readBytesUntil('\n', rxBufQrd, SZ_RXBUF_QRD);
        
        if (rxSize >= 1 && rxBufQrd[rxSize - 1] == '\r') { 
            // '\r'을 확인하고 0x0로 대체하여 String으로 인식되도록 해 둔다.
            rxBufQrd[rxSize - 1] = 0x0; // set END_OF_STRING
            String txStr = String(rxBufQrd);
            
            // 아래의 println은 출력 내용 끝에 '\r\n'이 추가된다.
            Serial.println(txStr); // to Debugger Console.
            SerialBT.println(txStr);

            memset(rxBufQrd, 0, SZ_RXBUF_QRD);
        }
    }
}
