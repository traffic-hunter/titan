/*
 * The MIT License
 *
 * Copyright (c) 2025 traffic-hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.traffichunter.titan.core.httpserver;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpStatus;
import org.traffichunter.titan.bootstrap.servicediscovery.SettingsServiceDiscovery;
import org.traffichunter.titan.bootstrap.servicediscovery.SettingsServiceDiscovery.Struct;
import org.traffichunter.titan.core.util.SerializeUtils;
import org.traffichunter.titan.servicediscovery.RoutingKey;
import org.traffichunter.titan.servicediscovery.RoutingTable;
import org.traffichunter.titan.servicediscovery.ServiceDiscovery;

/**
 * @author yungwang-o
 */
@Slf4j
public class ServiceDiscoveryServlet extends HttpServlet {

    private final ServiceDiscovery serviceDiscovery;

    public ServiceDiscoveryServlet(final SettingsServiceDiscovery settingsServiceDiscovery) {
        this.serviceDiscovery = new ServiceDiscovery(settingsServiceDiscovery);
    }

    // find service
    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) {

        try {

            List<RoutingTable> routingTables = serviceDiscovery.getService();

            String json = SerializeUtils.serialize(routingTables);

            resp.setContentType(ContentType.APPLICATION_JSON);
            resp.setStatus(HttpStatus.OK_200);
            resp.getWriter().write(json);
        } catch (Exception e) {
            log.error("Failed find service = {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // register service
    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) {

        try {

            RoutingTable routingTable = SerializeUtils.deserialize(req.getInputStream(), RoutingTable.class);

            serviceDiscovery.discover(routingTable);

            resp.setStatus(HttpServletResponse.SC_CREATED);
        } catch (Exception e) {
            log.error("Failed register service = {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // delete service
    @Override
    protected void doDelete(final HttpServletRequest req, final HttpServletResponse resp) {

        try {

            String routingKey = req.getParameter("routingKey");
            if (routingKey == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid routingKey");
                return;
            }

            RoutingKey key = RoutingKey.create(routingKey);

            serviceDiscovery.unDiscover(key);

            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception e) {
            log.error("Failed unregister service = {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // update service
    @Override
    protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {

        try {

            RoutingTable table = SerializeUtils.deserialize(req.getInputStream(), RoutingTable.class);

            serviceDiscovery.discover(table);

            resp.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            log.error("Failed update service = {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
