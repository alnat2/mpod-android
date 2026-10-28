#!/usr/bin/env python3
import socket
import threading
import sys

LISTEN_HOST = "0.0.0.0"
LISTEN_PORT = 5038
TARGET_HOST = "127.0.0.1"
TARGET_PORT = 5037

def forward(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try: src.close()
        except: pass
        try: dst.close()
        except: pass

def handle_client(client_sock):
    try:
        server_sock = socket.create_connection((TARGET_HOST, TARGET_PORT), timeout=5)
    except Exception as e:
        print(f"[Bridge] Could not connect to ADB on {TARGET_HOST}:{TARGET_PORT}: {e}")
        client_sock.close()
        return

    t1 = threading.Thread(target=forward, args=(client_sock, server_sock), daemon=True)
    t2 = threading.Thread(target=forward, args=(server_sock, client_sock), daemon=True)
    t1.start()
    t2.start()

def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind((LISTEN_HOST, LISTEN_PORT))
    except Exception as e:
        print(f"[Bridge] Failed to bind {LISTEN_HOST}:{LISTEN_PORT}: {e}")
        sys.exit(1)
    sock.listen(10)
    print(f"=== ADB Host Bridge active ===")
    print(f"Listening on all interfaces port {LISTEN_PORT} -> forwarding to ADB at {TARGET_HOST}:{TARGET_PORT}")
    print("Press Ctrl+C to stop.")
    while True:
        try:
            client, addr = sock.accept()
            threading.Thread(target=handle_client, args=(client,), daemon=True).start()
        except KeyboardInterrupt:
            print("\n[Bridge] Stopping.")
            break
        except Exception as e:
            print(f"[Bridge] Accept error: {e}")

if __name__ == "__main__":
    main()
