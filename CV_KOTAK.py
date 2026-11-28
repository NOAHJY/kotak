import cv2
camera = cv2.VideoCapture(0)

camera.set(cv2.CAP_PROP_FRAME_WIDTH,648)
camera.set(cv2.CAP_PROP_FRAME_HEIGHT,480)

while True:
    ret, frame = camera.read()
    
    if not ret:
        print("failed")
        break
    cv2.imshow("camera: percobaan", frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        print("\n progress dihentikan ")
        break
    elif cv2.getWindowProperty('camera: percobaan', cv2.WND_PROP_VISIBLE) < 1:
        break

camera.release()
cv2.destroyAllWindows()