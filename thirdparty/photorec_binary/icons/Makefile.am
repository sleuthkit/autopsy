EXTRA_DIST = photorec.ico testdisk.ico \
	     48x48/apps/qphotorec.png \
	     scalable/apps/qphotorec.svg

iconsdir = $(datadir)/icons/hicolor


gtk_update_icon_cache = gtk-update-icon-cache -f -t $(iconsdir)
if USEQT
  nobase_icons_DATA = 48x48/apps/qphotorec.png \
	scalable/apps/qphotorec.svg
  UPDATE_ICON_CACHE=update-icon-cache
endif

install-data-hook: $(UPDATE_ICON_CACHE)
uninstall-hook: $(UPDATE_ICON_CACHE)

update-icon-cache:
	@-if test -z "$(DESTDIR)"; then \
		echo "Updating GTK icon cache."; \
		$(gtk_update_icon_cache) || : ; \
	else \
		echo "*** Icon cache not updated. After (un)install, run this:"; \
		echo "*** $(gtk_update_icon_cache)"; \
	fi
