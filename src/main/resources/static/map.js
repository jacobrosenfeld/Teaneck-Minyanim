function initMap() {
    var property
    var geocoder = new google.maps.Geocoder();
    geocoder.geocode({'address': address}, function(results, status) {
      if (status === 'OK') {
        var map = new google.maps.Map(document.getElementById('map'), {
          zoom: 15,
          mapId: `ab35d05ad627f5db`,
          center: results[0].geometry.location
        });
        for (const property of properties) {
            const advancedMarkerView = new google.maps.marker.AdvancedMarkerView({
              map,
              position: results[0].geometry.location,
              title: marker,
            });
            const element = advancedMarkerView.element;
        
            ["focus", "pointerenter"].forEach((event) => {
              element.addEventListener(event, () => {
                highlight(advancedMarkerView, property);
              });
            });
            ["blur", "pointerleave"].forEach((event) => {
              element.addEventListener(event, () => {
                unhighlight(advancedMarkerView, property);
              });
            });
            advancedMarkerView.addListener("click", (event) => {
              unhighlight(advancedMarkerView, property);
            });
          }
        }
        
        function highlight(markerView, property) {
          markerView.content.classList.add("highlight");
          markerView.element.style.zIndex = 1;
        }
        
        function unhighlight(markerView, property) {
          markerView.content.classList.remove("highlight");
          markerView.element.style.zIndex = "";
        }
        
        function buildContent(property) {
          const content = document.createElement("div");
        
          content.classList.add("property");
          content.innerHTML = `
            <div class="icon">
                <i aria-hidden="true" class="fa fa-icon fa-building" th:each="minyan: ${allminyanim}" title="$${org.getName()}"></i>
                <span class="fa-sr-only">building</span>
            </div>
            <div class="details">
                <div class="price">Next Minyan</div>
                
                <div class="address" th:each="minyan: ${allminyanim}">${minyan.getType().displayName()[0]}</div>
                <div class="address" th:each="minyan: ${allminyanim}">at</div>
                <div class="address" th:each="minyan: ${allminyanim}">${minyan.getFormattedStartTime().displayName()[0]}</div>
                </div>
            </div>
            `;
          return content;
        }
        const properties = [
          {
            address: address,
            type: "building",
          },]
})}

window.initMap = initMap;
