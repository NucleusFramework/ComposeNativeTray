/* Exercise the public SNI protocol on a private session bus. */
#include "sni.h"
#include <systemd/sd-bus.h>
#include <assert.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static const char *path = "/StatusNotifierItem";
static const char *iface = "org.kde.StatusNotifierItem";
static atomic_int clicks, right_clicks;
static int changes, last_item_is_menu;
static int refresh_pending, refreshes, cached_item_is_menu;
static char service[128];

/* Model Plasma's host cache: refresh GetAll on NewTitle, ignoring
 * PropertiesChanged for routing clicks. See StatusNotifierItemSource in:
 * https://github.com/KDE/plasma-workspace/blob/v6.7.5/applets/systemtray/statusnotifieritemsource.cpp */
static void refresh_host_cache(sd_bus *bus) {
    sd_bus_message *reply = NULL;
    assert(sd_bus_call_method(bus, service, path, "org.freedesktop.DBus.Properties",
                              "GetAll", NULL, &reply, "s", iface) >= 0);
    assert(sd_bus_message_enter_container(reply, 'a', "{sv}") > 0);
    int found = 0;
    while (sd_bus_message_enter_container(reply, 'e', "sv") > 0) {
        const char *property;
        assert(sd_bus_message_read(reply, "s", &property) > 0);
        if (strcmp(property, "ItemIsMenu") == 0) {
            assert(sd_bus_message_enter_container(reply, 'v', "b") > 0);
            assert(sd_bus_message_read(reply, "b", &cached_item_is_menu) > 0);
            assert(sd_bus_message_exit_container(reply) >= 0);
            found = 1;
        } else {
            assert(sd_bus_message_skip(reply, "v") >= 0);
        }
        assert(sd_bus_message_exit_container(reply) >= 0);
    }
    assert(found);
    sd_bus_message_unref(reply);
}

static int new_title(sd_bus_message *message, void *data, sd_bus_error *error) {
    (void)message; (void)data; (void)error;
    refresh_pending = 1;
    refreshes++;
    return 0;
}

static void *run(void *tray) {
    assert(sni_tray_run(tray) == 0);
    return NULL;
}

static void click(int32_t x, int32_t y, void *data) {
    assert(x == 12 && y == 34);
    atomic_fetch_add(&clicks, 1);
    /* User callbacks must be able to update the tray without deadlocking. */
    if (data) sni_tray_set_click_callback(data, NULL, NULL);
}

static void right_click(int32_t x, int32_t y, void *data) {
    (void)data;
    assert(x == 12 && y == 34);
    atomic_fetch_add(&right_clicks, 1);
}

static int property_changed(sd_bus_message *message, void *data, sd_bus_error *error) {
    (void)data; (void)error;
    const char *interface, *property;
    assert(sd_bus_message_read(message, "s", &interface) > 0);
    assert(sd_bus_message_enter_container(message, 'a', "{sv}") > 0);
    while (sd_bus_message_enter_container(message, 'e', "sv") > 0) {
        assert(sd_bus_message_read(message, "s", &property) > 0);
        if (strcmp(interface, iface) == 0 && strcmp(property, "ItemIsMenu") == 0) {
            assert(sd_bus_message_enter_container(message, 'v', "b") > 0);
            assert(sd_bus_message_read(message, "b", &last_item_is_menu) > 0);
            assert(sd_bus_message_exit_container(message) >= 0);
            changes++;
        } else {
            assert(sd_bus_message_skip(message, "v") >= 0);
        }
        assert(sd_bus_message_exit_container(message) >= 0);
    }
    return 0;
}

static void pump(sd_bus *bus, int milliseconds) {
    for (int i = 0; i < milliseconds; i++) {
        int result;
        while ((result = sd_bus_process(bus, NULL)) > 0) {}
        assert(result >= 0);
        if (refresh_pending) {
            refresh_pending = 0;
            refresh_host_cache(bus);
        }
        usleep(1000);
    }
}

static void expect_property(sd_bus *bus, int expected) {
    int value = -1, result = -1;
    /* Registration happens asynchronously on the tray thread. */
    for (int i = 0; i < 200 && result < 0; i++) {
        result = sd_bus_get_property_trivial(bus, service, path, iface,
                                            "ItemIsMenu", NULL, 'b', &value);
        if (result < 0) usleep(10000);
    }
    assert(result >= 0 && value == expected);
}

static void call(sd_bus *bus, const char *method) {
    assert(sd_bus_call_method(bus, service, path, iface, method, NULL, NULL,
                              "ii", 12, 34) >= 0);
    pump(bus, 100);
}

static void host_click(sd_bus *bus) {
    call(bus, cached_item_is_menu ? "ContextMenu" : "Activate");
}

int main(void) {
    sd_bus *bus = NULL;
    sd_bus_slot *slot = NULL;
    sd_bus_slot *title_slot = NULL;
    assert(sd_bus_open_user(&bus) >= 0);
    snprintf(service, sizeof(service), "org.kde.StatusNotifierItem-%d-1", getpid());
    assert(sd_bus_match_signal(bus, &slot, service, path,
                              "org.freedesktop.DBus.Properties", "PropertiesChanged",
                              property_changed, NULL) >= 0);
    assert(sd_bus_match_signal(bus, &title_slot, service, path, iface,
                              "NewTitle", new_title, NULL) >= 0);
    sni_tray *tray = sni_tray_create(NULL, 0, "primary action test");
    assert(tray);
    sni_tray_set_rclick_callback(tray, right_click, NULL);
    pthread_t thread;
    assert(pthread_create(&thread, NULL, run, tray) == 0);
    expect_property(bus, 1);
    refresh_host_cache(bus);
    call(bus, "Activate");
    assert(atomic_load(&clicks) == 0);

    sni_tray_set_click_callback(tray, click, NULL);
    expect_property(bus, 0);
    pump(bus, 100);
    assert(changes == 1 && last_item_is_menu == 0);
    assert(refreshes == 1 && cached_item_is_menu == 0);
    host_click(bus);
    assert(atomic_load(&clicks) == 1);
    call(bus, "ContextMenu");
    assert(atomic_load(&right_clicks) == 1);

    /* Replacing a present callback must not announce a property transition. */
    sni_tray_set_click_callback(tray, click, tray);
    pump(bus, 100);
    assert(changes == 1 && refreshes == 1);
    host_click(bus);
    assert(atomic_load(&clicks) == 2);
    expect_property(bus, 1);
    assert(changes == 2 && last_item_is_menu == 1);
    assert(refreshes == 2 && cached_item_is_menu == 1);
    call(bus, "Activate");
    assert(atomic_load(&clicks) == 2);
    host_click(bus);
    assert(atomic_load(&right_clicks) == 2);

    sni_tray_quit(tray);
    pthread_join(thread, NULL);
    sni_tray_destroy(tray);

    /* A primary action installed before registration starts with ItemIsMenu=false. */
    tray = sni_tray_create(NULL, 0, "initial primary action");
    assert(tray);
    sni_tray_set_click_callback(tray, click, NULL);
    assert(pthread_create(&thread, NULL, run, tray) == 0);
    expect_property(bus, 0);
    call(bus, "Activate");
    assert(atomic_load(&clicks) == 3);
    sni_tray_quit(tray);
    pthread_join(thread, NULL);
    sni_tray_destroy(tray);
    sd_bus_slot_unref(slot);
    sd_bus_slot_unref(title_slot);
    sd_bus_unref(bus);
    puts("Primary action properties, host cache refresh, activation and context menu passed.");
    return 0;
}
