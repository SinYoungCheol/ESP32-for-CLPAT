#ifndef RGB_H
#define RGB_H
class RGB {
    public:
        void begin(int pin_red, int pin_green, int pin_blue);
        void setColor(int red, int green, int blue);
    private:
        int _pin_red, _pin_green, _pin_blue;
};

void RGB::begin(int pin_red, int pin_green, int pin_blue) {
    _pin_red = pin_red;
    _pin_green = pin_green;
    _pin_blue = pin_blue;

    pinMode(_pin_red, OUTPUT);
    pinMode(_pin_green, OUTPUT);
    pinMode(_pin_blue, OUTPUT);
}

void RGB::setColor(int red, int green, int blue) {
    analogWrite(_pin_red, red);
    analogWrite(_pin_green, green);
    analogWrite(_pin_blue, blue);
    delay(1000);
}
#endif // RGH_H