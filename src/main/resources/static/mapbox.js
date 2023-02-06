mapboxgl.accessToken = 'pk.eyJ1IjoiamFjb2Jyb3NlbmZlbGQiLCJhIjoiY2twbzVwNGJhMDdiNzJ4bzBtOGRzNjBjNSJ9.r8f10mthdHE69vYepqdq3Q';
let description;
if (typeof shulname !== 'undefined' && typeof minyantype !== 'undefined' && typeof minyantime !== 'undefined' && typeof address !== 'undefined') {
    description = "<h2>" + shulname + "</h2><p>Next Minyan: " + minyantype + " at " + minyantime + "</p><p>" + address + "</p>";
} else {
    description = "<h2>" + shulname + "</h2><h5>There are no more minyanim scheduled for today.</h5><p>" + address + "</p>"
}

// Make the GET request to the Mapbox Geocoding API
fetch(`https://api.mapbox.com/geocoding/v5/mapbox.places/${address}.json?access_token=${mapboxgl.accessToken}`)
    .then(response => response.json())
    .then(data => {
        // The data object returned from the API will contain an array of features
        // representing the geocoded addresses that match the query
        const geocode = data.features[0].geometry.coordinates;

        const map = new mapboxgl.Map({
            container: 'map',
            // Choose from Mapbox's core styles, or make your own style with Mapbox Studio
            style: 'mapbox://styles/mapbox/streets-v12',
            center: geocode,
            zoom: 15,
        });
        // Add a marker to the map
        // new mapboxgl.Marker()
        //   .setLngLat(geocode)
        //   .addTo(map);


        map.on('load', () => {
            map.addSource('places', {
                // This GeoJSON contains features that include an "icon"
                // property. The value of the "icon" property corresponds
                // to an image in the Mapbox Streets style's sprite.
                'type': 'geojson',
                'data': {
                    'type': 'FeatureCollection',
                    'features': [
                        {
                            'type': 'Feature',
                            'properties': {
                                'description': description,
                                'icon': 'border-dot-13'
                            },
                            'geometry': {
                                'type': 'Point',
                                'coordinates': geocode,
                            }
                        },
                    ]
                }
            });
            // Add a layer showing the places.
            map.addLayer({
                'id': 'places',
                'type': 'circle',
                'source': 'places',
                'paint': {
                    'circle-color': shulcolor,
                    'circle-radius': 12,
                    'circle-stroke-width': 2,
                    'circle-stroke-color': '#ffffff'
                }
            });

            // When a click event occurs on a feature in the places layer, open a popup at the
            // location of the feature, with description HTML from its properties.
            // map.on('click', 'places', (e) => {
            //     // Copy coordinates array.
            //     const coordinates = e.features[0].geometry.coordinates.slice();
            //     const description = e.features[0].properties.description;

            //     // Ensure that if the map is zoomed out such that multiple
            //     // copies of the feature are visible, the popup appears
            //     // over the copy being pointed to.
            //     while (Math.abs(e.lngLat.lng - coordinates[0]) > 180) {
            //         coordinates[0] += e.lngLat.lng > coordinates[0] ? 360 : -360;
            //     }

            //     new mapboxgl.Popup({ closeOnClick: false })
            //         .setLngLat(coordinates)
            //         .setHTML(description)
            //         .addTo(map);
            // });

            // Change the cursor to a pointer when the mouse is over the places layer.
            const popup = new mapboxgl.Popup({
                });

            map.on('mouseenter', 'places', (e) => {
                // Copy coordinates array.
                const coordinates = e.features[0].geometry.coordinates.slice();
                const description = e.features[0].properties.description;

                // Ensure that if the map is zoomed out such that multiple
                // copies of the feature are visible, the popup appears
                // over the copy being pointed to.
                while (Math.abs(e.lngLat.lng - coordinates[0]) > 180) {
                    coordinates[0] += e.lngLat.lng > coordinates[0] ? 360 : -360;
                }

                // new mapboxgl.Popup()
                //     .setLngLat(coordinates)
                //     .setHTML(description)
                //     .addTo(map);
                popup.setLngLat(coordinates).setHTML(description).addTo(map);

            });

            // Change it back to a pointer when it leaves.
            map.on('mouseleave', 'places', () => {
                map.getCanvas().style.cursor = '';
                popup.remove();
            });
            map.on('click', 'places', (e) => {
                // Copy coordinates array.
                const coordinates = e.features[0].geometry.coordinates.slice();
                const description = e.features[0].properties.description;

                // Ensure that if the map is zoomed out such that multiple
                // copies of the feature are visible, the popup appears
                // over the copy being pointed to.
                while (Math.abs(e.lngLat.lng - coordinates[0]) > 180) {
                    coordinates[0] += e.lngLat.lng > coordinates[0] ? 360 : -360;
                }

                // new mapboxgl.Popup()
                //     .setLngLat(coordinates)
                //     .setHTML(description)
                //     .addTo(map);
                popup.setLngLat(coordinates).setHTML(description).addTo(map);

            });

            // Change it back to a pointer when it leaves.
        });
    });